package com.estebanmm13.pytra_api.obsidian.service;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.model.Ratings;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.model.Saga;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.obsidian.dto.ObsidianExportDto;
import com.estebanmm13.pytra_api.obsidian.dto.ObsidianExportDto.ExperienceDto;
import com.estebanmm13.pytra_api.obsidian.dto.ObsidianExportDto.GameDto;
import com.estebanmm13.pytra_api.obsidian.dto.ObsidianExportDto.OnlineDto;
import com.estebanmm13.pytra_api.obsidian.dto.ObsidianExportDto.SagaDto;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the Obsidian sync snapshot: the user's CONFIRMED games (pending-review imports are left out, and so
 * are their runs and online playtime), the sagas those games belong to, and every run. Three queries, whatever
 * the library size: games with saga and genres, runs with platform, online playtime with platforms.
 */
@Service
public class ObsidianExportService {

    private static final Comparator<Platform> PLATFORM_ORDER = Comparator
            .comparing(Platform::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Platform::getName);

    private final GameRepository gameRepository;
    private final ExperienceRepository experienceRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;
    private final Clock clock;

    @Autowired
    public ObsidianExportService(GameRepository gameRepository,
                                 ExperienceRepository experienceRepository,
                                 OnlinePlaytimeRepository onlinePlaytimeRepository) {
        this(gameRepository, experienceRepository, onlinePlaytimeRepository, Clock.systemDefaultZone());
    }

    ObsidianExportService(GameRepository gameRepository,
                          ExperienceRepository experienceRepository,
                          OnlinePlaytimeRepository onlinePlaytimeRepository,
                          Clock clock) {
        this.gameRepository = gameRepository;
        this.experienceRepository = experienceRepository;
        this.onlinePlaytimeRepository = onlinePlaytimeRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ObsidianExportDto export(Long userId) {
        List<Game> games = gameRepository.findAllByUserIdAndReviewStatus(userId, ReviewStatus.CONFIRMED).stream()
                .sorted(Comparator.comparing(Game::getId))
                .toList();
        Set<Long> gameIds = games.stream().map(Game::getId).collect(Collectors.toSet());

        Map<Long, OnlinePlaytime> onlineByGame = onlinePlaytimeRepository.findAllByUserId(userId).stream()
                .filter(online -> gameIds.contains(online.getGameId()))
                .collect(Collectors.toMap(OnlinePlaytime::getGameId, Function.identity()));

        // TreeMap: sagas ordered by id; only those of exported games.
        Map<Long, Saga> sagas = games.stream()
                .map(Game::getSaga)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Saga::getId, Function.identity(), (a, b) -> a, TreeMap::new));

        List<ExperienceDto> experiences = experienceRepository.findAllByUserId(userId).stream()
                .filter(experience -> gameIds.contains(experience.getGameId()))
                .sorted(Comparator.comparing(Experience::getGameId).thenComparing(ExperiencePeriod.RECENCY))
                .map(this::toExperience)
                .toList();

        return new ObsidianExportDto(
                ObsidianExportDto.SCHEMA_VERSION,
                clock.instant().truncatedTo(ChronoUnit.SECONDS),
                sagas.values().stream().map(saga -> new SagaDto(saga.getId(), saga.getName(),
                        toInstant(saga.getUpdatedAt()))).toList(),
                games.stream().map(game -> toGame(game, onlineByGame.get(game.getId()))).toList(),
                experiences);
    }

    private GameDto toGame(Game game, OnlinePlaytime online) {
        LocalDateTime updatedAt = game.getUpdatedAt();
        if (online != null && online.getUpdatedAt() != null
                && (updatedAt == null || online.getUpdatedAt().isAfter(updatedAt))) {
            updatedAt = online.getUpdatedAt();
        }
        return new GameDto(
                game.getId(),
                game.getName(),
                game.getSaga() != null ? game.getSaga().getId() : null,
                game.getCategory() != null ? game.getCategory().name() : null,
                game.getGenres().stream().map(Genre::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                game.getDeveloper(),
                game.getPublisher(),
                game.getReleaseDate(),
                online != null ? toOnline(online) : null,
                toInstant(updatedAt));
    }

    private static OnlineDto toOnline(OnlinePlaytime online) {
        return new OnlineDto(
                online.getTotalHours(),
                online.getPlatforms().stream().sorted(PLATFORM_ORDER).map(Platform::getName).toList(),
                Ratings.normalize(online.getGeneralRating()),
                Boolean.TRUE.equals(online.getPlatinum()),
                Boolean.TRUE.equals(online.getFullCompletion()));
    }

    private ExperienceDto toExperience(Experience experience) {
        return new ExperienceDto(
                experience.getId(),
                experience.getGameId(),
                experience.getRunLabel(),
                ExperiencePeriod.yearOf(experience),
                experience.getStatus().name(),
                Ratings.normalize(experience.getRating()),
                experience.getHours(),
                experience.getStartDate(),
                experience.getEndDate(),
                experience.getPlatform() != null ? experience.getPlatform().getName() : null,
                Boolean.TRUE.equals(experience.getPlatinum()),
                Boolean.TRUE.equals(experience.getFullCompletion()),
                Boolean.TRUE.equals(experience.getReplay()),
                toInstant(experience.getUpdatedAt()));
    }

    /** Timestamps are stored as local date-times in the server zone (Hibernate's default for LocalDateTime). */
    private Instant toInstant(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.atZone(ZoneId.systemDefault()).toInstant() : null;
    }
}
