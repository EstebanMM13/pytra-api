package com.estebanmm13.pytra_api.psnsync.service;

import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.platformsync.EnrichmentProgress;
import com.estebanmm13.pytra_api.platformsync.PendingGameEnricher;
import com.estebanmm13.pytra_api.platformsync.PendingReviewQueue;
import com.estebanmm13.pytra_api.platformsync.PlatformLinkRemover;
import com.estebanmm13.pytra_api.psnsync.client.PsnAccount;
import com.estebanmm13.pytra_api.psnsync.client.PsnApiClient;
import com.estebanmm13.pytra_api.psnsync.client.PsnOnlineIds;
import com.estebanmm13.pytra_api.psnsync.dto.PsnStatusDto;
import com.estebanmm13.pytra_api.psnsync.model.PsnLink;
import com.estebanmm13.pytra_api.psnsync.repository.PsnIgnoredTitleRepository;
import com.estebanmm13.pytra_api.psnsync.repository.PsnLinkRepository;
import com.estebanmm13.pytra_api.steamsync.service.SteamSyncGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PsnLinkServiceImpl implements PsnLinkService {

    private final PsnLinkRepository psnLinkRepository;
    private final PsnIgnoredTitleRepository psnIgnoredTitleRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final PendingReviewQueue pendingReviewQueue;
    private final PlatformLinkRemover platformLinkRemover;
    private final PsnApiClient psnApiClient;
    private final SteamSyncGuard syncGuard;
    private final PlatformTransactionManager transactionManager;
    private final PendingGameEnricher pendingGameEnricher;

    /**
     * Service fields are always filled here; the controller strips them for non-admins (and the PSN
     * identity for the demo account).
     */
    @Override
    public PsnStatusDto getStatus(Long userId) {
        boolean configured = psnApiClient.isConfigured();
        // Counted even when unlinked: unlink clears all three, so they read 0 rather than null.
        long linkedGames = gamePlatformLinkRepository.countByUserIdAndPlatformIn(userId, PsnPlatforms.ALL);
        long pending = pendingReviewQueue.countPending(userId, PsnPlatforms.ALL, false);
        long ignored = psnIgnoredTitleRepository.countByUserId(userId);
        Instant authFailingSince = configured ? psnApiClient.serviceAuthFailingSince() : null;
        String authError = authFailingSince != null ? PsnIntegrationException.SERVICE_AUTH_FAILED : null;
        EnrichmentProgress enrichment = pendingGameEnricher.progress(userId);
        return psnLinkRepository.findByUserId(userId)
                .map(link -> new PsnStatusDto(true, link.getAccountId(), link.getOnlineId(), link.getLastSyncAt(),
                        configured, linkedGames, pending, ignored, authError, authFailingSince, enrichment))
                .orElseGet(() -> new PsnStatusDto(false, null, null, null,
                        configured, linkedGames, pending, ignored, authError, authFailingSince, enrichment));
    }

    /**
     * Links (or re-links) the PSN account with that online ID. The ID is resolved on PSN first (outside
     * any transaction), so a typo is rejected with PSN_USER_NOT_FOUND instead of stored.
     *
     * <p>Switching to a DIFFERENT account resets every PSN baseline, so it takes the sync guard (held
     * until the transaction commits): it must never interleave with a running sync. Linking the same
     * account again only refreshes the stored online ID (PSN accounts can be renamed).
     *
     * @throws PsnIntegrationException PSN_INVALID_ONLINE_ID, PSN_NOT_CONFIGURED, PSN_USER_NOT_FOUND,
     *                                 PSN_UNAVAILABLE / PSN_RATE_LIMITED; SYNC_IN_PROGRESS when switching while a sync runs
     */
    @Override
    public PsnStatusDto link(Long userId, String onlineId) {
        String normalized = PsnOnlineIds.normalize(onlineId);
        if (normalized == null) {
            throw PsnIntegrationException.invalidOnlineId();
        }
        if (!psnApiClient.isConfigured()) {
            throw PsnIntegrationException.notConfigured();
        }

        PsnAccount account = psnApiClient.findAccount(normalized);

        boolean switchingAccount = psnLinkRepository.findByUserId(userId)
                .map(link -> !link.getAccountId().equals(account.accountId()))
                .orElse(false);
        if (!switchingAccount) {
            transactionTemplate().execute(status -> doUpsertLink(userId, account));
        } else {
            syncGuard.runExclusive(userId,
                    () -> transactionTemplate().execute(status -> doUpsertLink(userId, account)));
        }
        return getStatus(userId);
    }

    private PsnLink doUpsertLink(Long userId, PsnAccount account) {
        Optional<PsnLink> existing = psnLinkRepository.findByUserId(userId);
        PsnLink psnLink = existing.orElseGet(() -> PsnLink.builder().userId(userId).build());

        boolean switching = existing.isPresent() && !existing.get().getAccountId().equals(account.accountId());
        if (switching) {
            // Different PSN account: the old baselines belong to another library.
            resetPsnData(userId);
            psnLink.setLastSyncAt(null);
        }
        if (existing.isEmpty() || switching) {
            psnLink.setLinkedAt(LocalDateTime.now());
        }

        psnLink.setAccountId(account.accountId());
        psnLink.setOnlineId(account.onlineId());
        return psnLinkRepository.saveAndFlush(psnLink);
    }

    /**
     * Removes the PSN link. Confirmed games keep everything (their hours are the user's data now);
     * only their PSN links go away, so a later re-link starts from fresh baselines and never
     * re-imports hours. PSN placeholders still pending review and the ignore list are deleted.
     */
    @Override
    public void unlink(Long userId) {
        // Guard held until the transaction has committed (a @Transactional method would release first).
        syncGuard.runExclusive(userId, () -> transactionTemplate().execute(status -> {
            PsnLink psnLink = psnLinkRepository.findByUserId(userId)
                    .orElseThrow(PsnIntegrationException::notLinked);
            resetPsnData(userId);
            psnLinkRepository.delete(psnLink);
            return null;
        }));
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    /**
     * Deletes the user's PSN links and ignore list. Pending placeholders Steam also links are kept,
     * see {@link PlatformLinkRemover}.
     */
    private void resetPsnData(Long userId) {
        platformLinkRemover.removeLinks(userId, PsnPlatforms.ALL,
                gamePlatformLinkRepository.findAllByUserIdAndPlatformInWithGame(userId, PsnPlatforms.ALL));
        psnIgnoredTitleRepository.deleteAllByUserId(userId);
    }
}
