package com.estebanmm13.pytra_api.psnsync.service;

import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.games.mapper.GameMapper;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platformsync.PendingGameConfirmer;
import com.estebanmm13.pytra_api.platformsync.PendingReviewQueue;
import com.estebanmm13.pytra_api.psnsync.client.PsnApiClient;
import com.estebanmm13.pytra_api.psnsync.client.PsnPlayedGamesResult;
import com.estebanmm13.pytra_api.psnsync.dto.PsnIgnoredTitleDto;
import com.estebanmm13.pytra_api.psnsync.dto.PsnPendingGameDto;
import com.estebanmm13.pytra_api.psnsync.dto.PsnSyncResultDto;
import com.estebanmm13.pytra_api.psnsync.model.PsnIgnoredTitle;
import com.estebanmm13.pytra_api.psnsync.model.PsnLink;
import com.estebanmm13.pytra_api.psnsync.repository.PsnIgnoredTitleRepository;
import com.estebanmm13.pytra_api.psnsync.repository.PsnLinkRepository;
import com.estebanmm13.pytra_api.steamsync.service.SteamSyncGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PsnSyncServiceImpl implements PsnSyncService {

    private final PsnLinkRepository psnLinkRepository;
    private final PsnApiClient psnApiClient;
    private final PsnLibraryImporter psnLibraryImporter;
    private final SteamSyncGuard syncGuard;
    private final PendingGameConfirmer pendingGameConfirmer;
    private final PendingReviewQueue pendingReviewQueue;
    private final GameRepository gameRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final PsnIgnoredTitleRepository psnIgnoredTitleRepository;
    private final GameMapper gameMapper;
    private final PlatformTransactionManager transactionManager;

    /**
     * Deliberately NOT transactional: the PSN HTTP calls run first, with no DB transaction open, and
     * only then {@link PsnLibraryImporter} applies the result in short transactions.
     */
    @Override
    public PsnSyncResultDto sync(Long userId) {
        PsnLink psnLink = psnLinkRepository.findByUserId(userId)
                .orElseThrow(PsnIntegrationException::notLinked);
        if (!psnApiClient.isConfigured()) {
            throw PsnIntegrationException.notConfigured();
        }

        String accountId = psnLink.getAccountId();
        return syncGuard.runExclusive(userId, () -> {
            PsnPlayedGamesResult library = psnApiClient.getPlayedGames(accountId);
            return psnLibraryImporter.importLibrary(userId, accountId, library);
        });
    }

    /**
     * PSN's slice of the shared pending queue: pending games with a PSN_PS4/PSN_PS5 link. Three
     * queries in total (pending games, the user's PSN game ids, the user's PSN links), joined in memory.
     */
    @Override
    public List<PsnPendingGameDto> getPending(Long userId) {
        Map<Long, List<GamePlatformLink>> linksByGameId = gamePlatformLinkRepository
                .findAllByUserIdAndPlatformInWithGame(userId, PsnPlatforms.ALL).stream()
                .collect(Collectors.groupingBy(link -> link.getGame().getId()));
        return pendingReviewQueue.findPending(userId, PsnPlatforms.ALL, false).stream()
                .map(game -> toPendingDto(game, linksByGameId.getOrDefault(game.getId(), List.of())))
                .toList();
    }

    private PsnPendingGameDto toPendingDto(Game game, List<GamePlatformLink> links) {
        GameResponseDto dto = gameMapper.toResponseDto(game);
        GamePlatformLink primary = links.stream()
                .min(Comparator.comparing(GamePlatformLink::getPlatform, PsnPlatforms.PS5_FIRST))
                .orElse(null);
        if (primary == null) {
            return PsnPendingGameDto.of(dto, null, null, null, null);
        }
        long minutes = links.stream().mapToLong(GamePlatformLink::getLastSyncedPlaytimeMinutes).sum();
        Instant lastPlayedAt = links.stream()
                .map(GamePlatformLink::getLastPlayedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return PsnPendingGameDto.of(dto, primary.getExternalId(), PsnPlatforms.consoleCode(primary.getPlatform()),
                minutes, lastPlayedAt);
    }

    /**
     * Guarded: a sync running at the same time would race on the same link baseline. Imports the
     * synced total of every link of the game (PS4, PS5 and Steam), see {@link PendingGameConfirmer}.
     */
    @Override
    public GameResponseDto confirmPending(Long gameId, GameRequestDto gameRequestDto, Long userId) {
        return syncGuard.runExclusive(userId, () -> transactionTemplate().execute(
                status -> pendingGameConfirmer.confirm(gameId, gameRequestDto, userId)));
    }

    /**
     * Records the game's PSN titles as ignored and removes their links, so the sync never recreates
     * them. The placeholder is deleted too, unless Steam still links it (it then stays in Steam's queue).
     * Only pending games with a PSN link can be ignored here; anything else is a 404.
     */
    @Override
    public void ignorePending(Long gameId, Long userId) {
        syncGuard.runExclusive(userId, () -> transactionTemplate().execute(status -> {
            doIgnorePending(gameId, userId);
            return null;
        }));
    }

    private void doIgnorePending(Long gameId, Long userId) {
        Game game = gameRepository.findByIdAndUserId(gameId, userId)
                .filter(g -> g.getReviewStatus() == ReviewStatus.PENDING_REVIEW)
                .orElseThrow(() -> new ResourceNotFoundException("Pending game not found"));
        List<GamePlatformLink> links = gamePlatformLinkRepository.findAllByUserIdAndGameId(userId, gameId);
        List<GamePlatformLink> psnLinks = links.stream()
                .filter(link -> PsnPlatforms.ALL.contains(link.getPlatform()))
                .toList();
        if (psnLinks.isEmpty()) {
            throw new ResourceNotFoundException("Pending game not found");
        }

        LocalDateTime now = LocalDateTime.now();
        for (GamePlatformLink link : psnLinks) {
            if (!psnIgnoredTitleRepository.existsByUserIdAndTitleId(userId, link.getExternalId())) {
                psnIgnoredTitleRepository.save(PsnIgnoredTitle.builder()
                        .userId(userId)
                        .titleId(link.getExternalId())
                        .platform(link.getPlatform())
                        .name(game.getName())
                        .ignoredAt(now)
                        .build());
            }
            gamePlatformLinkRepository.delete(link);
        }
        if (links.size() == psnLinks.size()) {
            gameRepository.delete(game);
        }
    }

    @Override
    public List<PsnIgnoredTitleDto> getIgnored(Long userId) {
        return psnIgnoredTitleRepository.findAllByUserIdOrderByNameAsc(userId).stream()
                .map(title -> new PsnIgnoredTitleDto(title.getTitleId(), PsnPlatforms.consoleCode(title.getPlatform()),
                        title.getName(), title.getIgnoredAt()))
                .toList();
    }

    /** The title comes back as a pending game on the next sync. */
    @Override
    @Transactional
    public void unignore(String titleId, Long userId) {
        PsnIgnoredTitle ignored = psnIgnoredTitleRepository.findByUserIdAndTitleId(userId, titleId)
                .orElseThrow(() -> new ResourceNotFoundException("Ignored title not found"));
        psnIgnoredTitleRepository.delete(ignored);
    }

    /** Guarded work must commit before the guard is released, hence a template, not @Transactional. */
    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
