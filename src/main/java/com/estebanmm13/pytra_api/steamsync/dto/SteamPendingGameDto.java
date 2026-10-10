package com.estebanmm13.pytra_api.steamsync.dto;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.Platform;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.games.dto.genre.GenreResponseDto;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A Steam placeholder game awaiting review: the same fields as {@link GameResponseDto} plus what
 * Steam knows about the app, so the client can show it before the user confirms.
 *
 * <p>Not a subclass of GameResponseDto on purpose: here {@code lastPlayedAt} is Steam's timestamp
 * ({@link Instant}), while on /games it is the date of the latest experience ({@link LocalDate}).
 * A pending game has no experiences, so the experience-based value would always be null anyway.
 *
 * @param appId                Steam appid (as stored on the platform link)
 * @param steamPlaytimeMinutes Steam's latest known total playtime for the app
 * @param lastPlayedAt         last time played according to Steam; null when never played or not reported yet
 */
public record SteamPendingGameDto(
        Long id,
        String name,
        String developer,
        String publisher,
        LocalDate releaseDate,
        GameCategory category,
        Long sagaId,
        String sagaName,
        String coverImageUrl,
        ReviewStatus reviewStatus,
        List<GenreResponseDto> genres,
        LocalDateTime updatedAt,
        Long experienceCount,
        Double totalHours,
        BigDecimal bestRating,
        ExperienceStatus lastExperienceStatus,
        Integer lastPlayedYear,
        Boolean hasPlatinum,
        List<Platform> platforms,
        String appId,
        Long steamPlaytimeMinutes,
        Instant lastPlayedAt
) {

    public static SteamPendingGameDto of(GameResponseDto game, String appId, Long steamPlaytimeMinutes, Instant lastPlayedAt) {
        return new SteamPendingGameDto(
                game.getId(), game.getName(), game.getDeveloper(), game.getPublisher(), game.getReleaseDate(),
                game.getCategory(), game.getSagaId(), game.getSagaName(), game.getCoverImageUrl(),
                game.getReviewStatus(), game.getGenres(), game.getUpdatedAt(),
                game.getExperienceCount(), game.getTotalHours(), game.getBestRating(),
                game.getLastExperienceStatus(), game.getLastPlayedYear(), game.getHasPlatinum(),
                game.getPlatforms(),
                appId, steamPlaytimeMinutes, lastPlayedAt);
    }
}
