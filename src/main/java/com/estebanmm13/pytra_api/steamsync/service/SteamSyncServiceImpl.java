package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.games.mapper.GameMapper;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platformsync.PendingGameConfirmer;
import com.estebanmm13.pytra_api.platformsync.PendingReviewQueue;
import com.estebanmm13.pytra_api.platformsync.PlatformLinkRemover;
import com.estebanmm13.pytra_api.steamsync.client.SteamOwnedGamesResult;
import com.estebanmm13.pytra_api.steamsync.client.SteamWebApiClient;
import com.estebanmm13.pytra_api.steamsync.dto.SteamIgnoredAppDto;
import com.estebanmm13.pytra_api.steamsync.dto.SteamPendingGameDto;
import com.estebanmm13.pytra_api.steamsync.dto.SteamSyncResultDto;
import com.estebanmm13.pytra_api.steamsync.model.SteamIgnoredApp;
import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import com.estebanmm13.pytra_api.steamsync.repository.SteamIgnoredAppRepository;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamSyncServiceImpl implements SteamSyncService {

    static final Set<ExternalPlatform> STEAM_PLATFORMS = Set.of(ExternalPlatform.STEAM);

    private final SteamLinkRepository steamLinkRepository;
    private final SteamWebApiClient steamWebApiClient;
    private final SteamLibraryImporter steamLibraryImporter;
    private final SteamSyncGuard steamSyncGuard;
    private final PendingGameConfirmer pendingGameConfirmer;
    private final PendingReviewQueue pendingReviewQueue;
    private final PlatformLinkRemover platformLinkRemover;
    private final GameRepository gameRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final SteamIgnoredAppRepository steamIgnoredAppRepository;
    private final GameMapper gameMapper;
    private final PlatformTransactionManager transactionManager;

    /**
     * Deliberately NOT transactional: the Steam HTTP call runs first, with no DB transaction
     * open, and only then {@link SteamLibraryImporter} applies the result in short transactions.
     */
    @Override
    public SteamSyncResultDto sync(Long userId) {
        SteamLink steamLink = steamLinkRepository.findByUserId(userId)
                .orElseThrow(SteamIntegrationException::notLinked);
        if (!steamWebApiClient.isConfigured()) {
            throw SteamIntegrationException.notConfigured();
        }

        String steamId = steamLink.getSteamId();
        return steamSyncGuard.runExclusive(userId, () -> {
            SteamOwnedGamesResult library = steamWebApiClient.getOwnedGames(steamId);
            return steamLibraryImporter.importLibrary(userId, steamId, library);
        });
    }

    /**
     * Steam's slice of the shared pending queue: pending games with a STEAM link, plus pending games
     * with no link at all (as before PSN existed). Games only linked to PSN belong to the PSN queue.
     */
    @Override
    public List<SteamPendingGameDto> getPending(Long userId) {
        Map<Long, GamePlatformLink> linksByGameId = gamePlatformLinkRepository
                .findAllByUserIdAndPlatformWithGame(userId, ExternalPlatform.STEAM).stream()
                .collect(Collectors.toMap(link -> link.getGame().getId(), Function.identity(), (a, b) -> a));
        return pendingReviewQueue.findPending(userId, STEAM_PLATFORMS, true).stream()
                .map(game -> {
                    GamePlatformLink link = linksByGameId.get(game.getId());
                    return SteamPendingGameDto.of(
                            gameMapper.toResponseDto(game),
                            link != null ? link.getExternalId() : null,
                            link != null ? link.getLastSyncedPlaytimeMinutes() : null,
                            link != null ? link.getLastPlayedAt() : null);
                })
                .toList();
    }

    /**
     * Guarded: a sync running at the same time would race on the same link baseline. Imports the
     * synced total of every link of the game (Steam and PSN), see {@link PendingGameConfirmer}.
     */
    @Override
    public GameResponseDto confirmPending(Long gameId, GameRequestDto gameRequestDto, Long userId) {
        return steamSyncGuard.runExclusive(userId, () -> transactionTemplate().execute(
                status -> pendingGameConfirmer.confirm(gameId, gameRequestDto, userId)));
    }

    /**
     * Records the appid as ignored and deletes its link and the pending placeholder, so it disappears
     * from both the pending list and the games list and the sync never recreates it. A placeholder that
     * PSN also links is kept (it stays in PSN's queue), see {@link PlatformLinkRemover}.
     * Only Steam placeholders still pending review can be ignored; anything else is a 404.
     * Guarded: deleting a link a running sync is updating would fail that sync.
     */
    @Override
    public void ignorePending(Long gameId, Long userId) {
        steamSyncGuard.runExclusive(userId, () -> transactionTemplate().execute(status -> {
            doIgnorePending(gameId, userId);
            return null;
        }));
    }

    private void doIgnorePending(Long gameId, Long userId) {
        Game game = gameRepository.findByIdAndUserId(gameId, userId)
                .filter(g -> g.getReviewStatus() == ReviewStatus.PENDING_REVIEW)
                .orElseThrow(() -> new ResourceNotFoundException("Pending game not found"));
        GamePlatformLink link = gamePlatformLinkRepository
                .findByUserIdAndGameIdAndPlatform(userId, gameId, ExternalPlatform.STEAM)
                .orElseThrow(() -> new ResourceNotFoundException("Pending game not found"));

        if (!steamIgnoredAppRepository.existsByUserIdAndAppId(userId, link.getExternalId())) {
            steamIgnoredAppRepository.save(SteamIgnoredApp.builder()
                    .userId(userId)
                    .appId(link.getExternalId())
                    .name(game.getName())
                    .ignoredAt(LocalDateTime.now())
                    .build());
        }
        platformLinkRemover.removeLinks(userId, STEAM_PLATFORMS, List.of(link));
    }

    @Override
    public List<SteamIgnoredAppDto> getIgnored(Long userId) {
        return steamIgnoredAppRepository.findAllByUserIdOrderByNameAsc(userId).stream()
                .map(app -> new SteamIgnoredAppDto(app.getAppId(), app.getName(), app.getIgnoredAt()))
                .toList();
    }

    /** The app comes back as a pending game on the next sync. */
    @Override
    @Transactional
    public void unignore(String appId, Long userId) {
        SteamIgnoredApp ignored = steamIgnoredAppRepository.findByUserIdAndAppId(userId, appId)
                .orElseThrow(() -> new ResourceNotFoundException("Ignored app not found"));
        steamIgnoredAppRepository.delete(ignored);
    }

    /** Guarded work must commit before the guard is released, hence a template, not @Transactional. */
    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
