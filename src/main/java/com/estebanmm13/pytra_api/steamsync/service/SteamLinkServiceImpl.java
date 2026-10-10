package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.auth.security.TokenGenerator;
import com.estebanmm13.pytra_api.error.InvalidTokenException;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.steamsync.client.SteamWebApiClient;
import com.estebanmm13.pytra_api.steamsync.dto.SteamStatusDto;
import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import com.estebanmm13.pytra_api.steamsync.model.SteamLinkState;
import com.estebanmm13.pytra_api.steamsync.repository.SteamIgnoredAppRepository;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SteamLinkServiceImpl implements SteamLinkService {

    private final SteamLinkStateRepository steamLinkStateRepository;
    private final SteamLinkRepository steamLinkRepository;
    private final SteamIgnoredAppRepository steamIgnoredAppRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final GameRepository gameRepository;
    private final SteamWebApiClient steamWebApiClient;
    private final SteamSyncGuard steamSyncGuard;
    private final TokenGenerator tokenGenerator;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional
    public String createLinkState(Long userId) {
        // Housekeeping piggybacked on the (rare) connect flow: states only live 10 minutes.
        steamLinkStateRepository.deleteExpired(LocalDateTime.now());

        String rawToken = tokenGenerator.generateTokenRaw();
        String tokenHash = tokenGenerator.hashToken(rawToken);

        SteamLinkState state = SteamLinkState.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        steamLinkStateRepository.save(state);

        return rawToken;
    }

    @Override
    public Long consumeLinkState(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("Invalid Steam link state");
        }

        String tokenHash = tokenGenerator.hashToken(rawToken);
        SteamLinkState state = steamLinkStateRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid Steam link state"));

        if (state.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Invalid Steam link state");
        }
        if (state.getConsumedAt() != null) {
            throw new InvalidTokenException("Invalid Steam link state");
        }

        state.setConsumedAt(LocalDateTime.now());
        steamLinkStateRepository.save(state);

        return state.getUserId();
    }

    /**
     * Links (or re-links) the user's Steam account.
     *
     * <p>Switching to a DIFFERENT Steam account resets every Steam baseline, so it takes the sync
     * guard (held until the transaction commits): it must never interleave with a running sync.
     *
     * @throws SteamIntegrationException ACCOUNT_ALREADY_LINKED if another Pytra user owns that Steam
     *                                   account, SYNC_IN_PROGRESS if switching while a sync runs
     */
    @Override
    public SteamLink upsertLink(Long userId, String steamId64, String personaName) {
        boolean switchingAccount = steamLinkRepository.findByUserId(userId)
                .map(link -> !link.getSteamId().equals(steamId64))
                .orElse(false);
        if (!switchingAccount) {
            return transactionTemplate().execute(status -> doUpsertLink(userId, steamId64, personaName));
        }
        return steamSyncGuard.runExclusive(userId,
                () -> transactionTemplate().execute(status -> doUpsertLink(userId, steamId64, personaName)));
    }

    private SteamLink doUpsertLink(Long userId, String steamId64, String personaName) {
        steamLinkRepository.findBySteamId(steamId64)
                .filter(link -> !link.getUserId().equals(userId))
                .ifPresent(link -> {
                    throw SteamIntegrationException.accountAlreadyLinked();
                });

        Optional<SteamLink> existing = steamLinkRepository.findByUserId(userId);
        SteamLink steamLink = existing.orElseGet(() -> SteamLink.builder().userId(userId).build());

        if (existing.isPresent() && !existing.get().getSteamId().equals(steamId64)) {
            // Different Steam account: the old baselines belong to another library.
            resetSteamData(userId);
            steamLink.setLastSyncAt(null);
        }

        steamLink.setSteamId(steamId64);
        steamLink.setPersonaName(personaName);
        steamLink.setLinkedAt(LocalDateTime.now());

        // Flush now so a concurrent link of the same Steam account fails here (unique steam_id).
        return steamLinkRepository.saveAndFlush(steamLink);
    }

    @Override
    public SteamStatusDto getStatus(Long userId) {
        boolean configured = steamWebApiClient.isConfigured();
        // Counted even when unlinked: unlink clears all three, so they read 0 rather than null.
        long linkedGames = gamePlatformLinkRepository.countByUserIdAndPlatform(userId, ExternalPlatform.STEAM);
        long pending = gameRepository.countByUserIdAndReviewStatus(userId, ReviewStatus.PENDING_REVIEW);
        long ignored = steamIgnoredAppRepository.countByUserId(userId);
        return steamLinkRepository.findByUserId(userId)
                .map(link -> new SteamStatusDto(true, link.getSteamId(), link.getPersonaName(), link.getLastSyncAt(),
                        configured, linkedGames, pending, ignored))
                .orElseGet(() -> new SteamStatusDto(false, null, null, null, configured, linkedGames, pending, ignored));
    }

    /**
     * Removes the Steam link. Confirmed games keep everything (their hours, experiences and
     * online playtime are the user's data now); only their STEAM links go away, so a later
     * re-link starts from fresh baselines and never re-imports hours. Steam-only placeholders
     * still pending review and the ignore list are deleted: they only made sense for that account.
     */
    @Override
    public void unlink(Long userId) {
        // Guard held until the transaction has committed (a @Transactional method would release first).
        steamSyncGuard.runExclusive(userId, () -> transactionTemplate().execute(status -> {
            SteamLink steamLink = steamLinkRepository.findByUserId(userId)
                    .orElseThrow(SteamIntegrationException::notLinked);
            resetSteamData(userId);
            steamLinkRepository.delete(steamLink);
            return null;
        }));
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private void resetSteamData(Long userId) {
        for (GamePlatformLink link : gamePlatformLinkRepository.findAllByUserIdAndPlatformWithGame(userId, ExternalPlatform.STEAM)) {
            Game game = link.getGame();
            gamePlatformLinkRepository.delete(link);
            if (game.getReviewStatus() == ReviewStatus.PENDING_REVIEW) {
                gameRepository.delete(game);
            }
        }
        steamIgnoredAppRepository.deleteAllByUserId(userId);
    }
}
