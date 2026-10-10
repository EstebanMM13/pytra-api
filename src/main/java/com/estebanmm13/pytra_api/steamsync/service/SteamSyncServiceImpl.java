package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.error.DuplicateResourceException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.games.mapper.GameMapper;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.model.Saga;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.GenreRepository;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamSyncServiceImpl implements SteamSyncService {

    private final SteamLinkRepository steamLinkRepository;
    private final SteamWebApiClient steamWebApiClient;
    private final SteamLibraryImporter steamLibraryImporter;
    private final SteamSyncGuard steamSyncGuard;
    private final SteamPlaytimeWriter steamPlaytimeWriter;
    private final GameRepository gameRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final SteamIgnoredAppRepository steamIgnoredAppRepository;
    private final SagaRepository sagaRepository;
    private final GenreRepository genreRepository;
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

    /** Two queries in total (pending games + the user's STEAM links), joined in memory. */
    @Override
    public List<SteamPendingGameDto> getPending(Long userId) {
        Map<Long, GamePlatformLink> linksByGameId = gamePlatformLinkRepository
                .findAllByUserIdAndPlatformWithGame(userId, ExternalPlatform.STEAM).stream()
                .collect(Collectors.toMap(link -> link.getGame().getId(), Function.identity(), (a, b) -> a));
        return gameRepository.findAllByUserIdAndReviewStatus(userId, ReviewStatus.PENDING_REVIEW).stream()
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

    /** Guarded: a sync running at the same time would race on the same link baseline. */
    @Override
    public GameResponseDto confirmPending(Long gameId, GameRequestDto gameRequestDto, Long userId) {
        return steamSyncGuard.runExclusive(userId, () -> transactionTemplate().execute(
                status -> doConfirmPending(gameId, gameRequestDto, userId)));
    }

    private GameResponseDto doConfirmPending(Long gameId, GameRequestDto gameRequestDto, Long userId) {
        Game game = gameRepository.findByIdAndUserId(gameId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Game not found"));

        if (gameRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, gameRequestDto.getName(), gameId)) {
            throw new DuplicateResourceException("Game already exists");
        }

        boolean wasPending = game.getReviewStatus() == ReviewStatus.PENDING_REVIEW;

        game.setName(gameRequestDto.getName());
        game.setDeveloper(gameRequestDto.getDeveloper());
        game.setPublisher(gameRequestDto.getPublisher());
        game.setReleaseDate(gameRequestDto.getReleaseDate());
        game.setCategory(gameRequestDto.getCategory());
        game.setSaga(resolveSaga(gameRequestDto.getSagaId(), userId));
        // Keep the cover set at import (Steam header) unless the client sends a new one.
        String cover = gameRequestDto.getCoverImageUrl();
        if (cover != null && !cover.isBlank()) {
            game.setCoverImageUrl(cover);
        }
        game.setGenres(resolveGenres(gameRequestDto.getGenreIds()));
        game.setReviewStatus(ReviewStatus.CONFIRMED);
        gameRepository.save(game);

        if (wasPending) {
            gamePlatformLinkRepository.findByUserIdAndGameIdAndPlatform(userId, game.getId(), ExternalPlatform.STEAM)
                    .ifPresent(link -> steamPlaytimeWriter.importTotalOnConfirm(link, game));
        }

        return gameMapper.toResponseDto(game);
    }

    /**
     * Records the appid as ignored and deletes the pending placeholder (and its link), so it
     * disappears from both the pending list and the games list and the sync never recreates it.
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
        gamePlatformLinkRepository.delete(link);
        gameRepository.delete(game);
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

    private Saga resolveSaga(Long sagaId, Long userId) {
        if (sagaId == null) return null;
        return sagaRepository.findByIdAndUserId(sagaId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Saga not found"));
    }

    private Set<Genre> resolveGenres(Set<Long> genreIds) {
        if (genreIds == null || genreIds.isEmpty()) return new HashSet<>();
        List<Genre> foundGenres = genreRepository.findAllById(genreIds);
        if (foundGenres.size() != genreIds.size()) {
            throw new ResourceNotFoundException("Genre not found");
        }
        return new HashSet<>(foundGenres);
    }
}
