package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.model.Platform;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.games.mapper.GameMapper;
import com.estebanmm13.pytra_api.games.model.*;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.GenreRepository;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import com.estebanmm13.pytra_api.steamsync.client.SteamOwnedGame;
import com.estebanmm13.pytra_api.steamsync.client.SteamWebApiClient;
import com.estebanmm13.pytra_api.steamsync.dto.SteamSyncResultDto;
import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Las horas sincronizadas de Steam nunca se reparten entre varias Experience:
 * eso exigiría adivinar a qué partida pertenece cada hora, algo que el dato de
 * Steam (un único total acumulado por juego) no permite saber con certeza.
 * En su lugar, cada GamePlatformLink de un juego SINGLEPLAYER mantiene al día
 * UNA única Experience "canónica" (GamePlatformLink.experienceId), separada
 * de cualquier partida creada a mano por el usuario. Para ONLINE/HYBRID, las
 * horas van a OnlinePlaytime (ya es un total único por juego, mismo shape que
 * el dato de Steam).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SteamSyncServiceImpl implements SteamSyncService {

    private static final String STEAM_IMPORT_NOTE = "Horas importadas automáticamente desde Steam";

    private final SteamLinkRepository steamLinkRepository;
    private final SteamWebApiClient steamWebApiClient;
    private final GameRepository gameRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final SagaRepository sagaRepository;
    private final GenreRepository genreRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;
    private final ExperienceRepository experienceRepository;
    private final GameMapper gameMapper;

    @Override
    @Transactional
    public SteamSyncResultDto sync(Long userId) {
        SteamLink steamLink = steamLinkRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Steam account not linked"));

        List<SteamOwnedGame> ownedGames = steamWebApiClient.getOwnedGames(steamLink.getSteamId());

        int scanned = 0;
        int newPending = 0;
        int updated = 0;

        for (SteamOwnedGame ownedGame : ownedGames) {
            scanned++;
            String externalId = String.valueOf(ownedGame.appId());

            Optional<GamePlatformLink> existingLink = gamePlatformLinkRepository
                    .findByUserIdAndPlatformAndExternalId(userId, ExternalPlatform.STEAM, externalId);

            if (existingLink.isEmpty()) {
                Game game = Game.builder()
                        .userId(userId)
                        .name(ownedGame.name())
                        .category(null)
                        .reviewStatus(ReviewStatus.PENDING_REVIEW)
                        .genres(new HashSet<>())
                        .build();
                gameRepository.save(game);

                GamePlatformLink link = GamePlatformLink.builder()
                        .userId(userId)
                        .game(game)
                        .platform(ExternalPlatform.STEAM)
                        .externalId(externalId)
                        .lastSyncedPlaytimeMinutes(ownedGame.playtimeForeverMinutes())
                        .lastSyncedAt(LocalDateTime.now())
                        .build();
                gamePlatformLinkRepository.save(link);

                newPending++;
            } else {
                GamePlatformLink link = existingLink.get();
                long delta = ownedGame.playtimeForeverMinutes() - link.getLastSyncedPlaytimeMinutes();
                link.setLastSyncedPlaytimeMinutes(ownedGame.playtimeForeverMinutes());
                link.setLastSyncedAt(LocalDateTime.now());
                gamePlatformLinkRepository.save(link);

                if (delta > 0 && link.getGame().getReviewStatus() == ReviewStatus.CONFIRMED) {
                    applyDelta(link, delta / 60.0);
                    updated++;
                }
            }
        }

        return new SteamSyncResultDto(scanned, newPending, updated);
    }

    @Override
    public List<GameResponseDto> getPending(Long userId) {
        List<Game> pendingGames = gameRepository.findAllByUserIdAndReviewStatus(userId, ReviewStatus.PENDING_REVIEW);
        List<GameResponseDto> result = new ArrayList<>();
        for (Game game : pendingGames) {
            result.add(gameMapper.toResponseDto(game));
        }
        return result;
    }

    @Override
    @Transactional
    public GameResponseDto confirmPending(Long gameId, GameRequestDto gameRequestDto, Long userId) {
        Game game = gameRepository.findByIdAndUserId(gameId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Game not found"));

        boolean wasPending = game.getReviewStatus() == ReviewStatus.PENDING_REVIEW;

        game.setName(gameRequestDto.getName());
        game.setDeveloper(gameRequestDto.getDeveloper());
        game.setPublisher(gameRequestDto.getPublisher());
        game.setReleaseDate(gameRequestDto.getReleaseDate());
        game.setCategory(gameRequestDto.getCategory());
        game.setSaga(resolveSaga(gameRequestDto.getSagaId(), userId));
        game.setCoverImageUrl(gameRequestDto.getCoverImageUrl());
        game.setGenres(resolveGenres(gameRequestDto.getGenreIds()));
        game.setReviewStatus(ReviewStatus.CONFIRMED);
        gameRepository.save(game);

        if (wasPending) {
            gamePlatformLinkRepository.findByUserIdAndGameId(userId, game.getId())
                    .ifPresent(link -> materializeAccumulatedHours(link, game));
        }

        return gameMapper.toResponseDto(game);
    }

    private void materializeAccumulatedHours(GamePlatformLink link, Game game) {
        double accumulatedHours = link.getLastSyncedPlaytimeMinutes() / 60.0;

        if (game.getCategory() == GameCategory.SINGLEPLAYER) {
            Experience experience = Experience.builder()
                    .userId(link.getUserId())
                    .gameId(game.getId())
                    .runLabel("Importado de Steam")
                    .status(ExperienceStatus.EN_CURSO)
                    .hours(accumulatedHours)
                    .platform(Platform.PC)
                    .platinum(false)
                    .replay(false)
                    .notes(STEAM_IMPORT_NOTE)
                    .build();
            experienceRepository.save(experience);

            link.setExperienceId(experience.getId());
            gamePlatformLinkRepository.save(link);
        } else {
            addHoursToOnlinePlaytime(link.getUserId(), game.getId(), accumulatedHours, link.getLastSyncedAt());
        }
    }

    private void applyDelta(GamePlatformLink link, double hoursToAdd) {
        GameCategory category = link.getGame().getCategory();

        if (category == GameCategory.SINGLEPLAYER) {
            if (link.getExperienceId() == null) {
                // El juego se confirmó como SINGLEPLAYER por fuera de este flujo
                // (p. ej. editado directamente en /api/v1/games) sin pasar por
                // confirmPending, así que no hay Experience canónica que actualizar.
                log.warn("GamePlatformLink {} es SINGLEPLAYER sin experienceId; delta de {} min no aplicado",
                        link.getId(), hoursToAdd * 60);
                return;
            }
            experienceRepository.findById(link.getExperienceId()).ifPresent(experience -> {
                experience.setHours(experience.getHours() + hoursToAdd);
                experienceRepository.save(experience);
            });
        } else {
            addHoursToOnlinePlaytime(link.getUserId(), link.getGame().getId(), hoursToAdd, link.getLastSyncedAt());
        }
    }

    private void addHoursToOnlinePlaytime(Long userId, Long gameId, double hoursToAdd, LocalDateTime sessionAt) {
        OnlinePlaytime onlinePlaytime = onlinePlaytimeRepository.findByGameIdAndUserId(gameId, userId)
                .orElseGet(() -> OnlinePlaytime.builder().userId(userId).gameId(gameId).totalHours(0.0).build());

        onlinePlaytime.setTotalHours(onlinePlaytime.getTotalHours() + hoursToAdd);
        onlinePlaytime.setLastSessionAt(sessionAt);
        onlinePlaytimeRepository.save(onlinePlaytime);
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
