package com.estebanmm13.pytra_api.psnsync.dto;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.games.dto.genre.GenreResponseDto;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.platforms.dto.PlatformRefDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A PSN placeholder game awaiting review: the same fields as {@link GameResponseDto} plus what PSN
 * knows about it (mirrors SteamPendingGameDto). A game played on PS4 and PS5 has two titles; the PSN
 * fields then describe both: the PS5 title id/console, the summed playtime and the latest play date.
 *
 * @param titleId            PSN title id (the PS5 one when the game has both)
 * @param psnPlatform        catalog legacy code of that title's console, "PS5" or "PS4": the platform the
 *                           imported run will be created on
 * @param psnPlaytimeMinutes PSN's latest known playtime, summed over the game's PSN titles
 * @param lastPlayedAt       last time played according to PSN; null when not reported
 */
public record PsnPendingGameDto(
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
        Boolean hasFullCompletion,
        List<String> platforms,
        List<PlatformRefDto> platformRefs,
        String titleId,
        String psnPlatform,
        Long psnPlaytimeMinutes,
        Instant lastPlayedAt
) {

    public static PsnPendingGameDto of(GameResponseDto game, String titleId, String psnPlatform,
                                       Long psnPlaytimeMinutes, Instant lastPlayedAt) {
        return new PsnPendingGameDto(
                game.getId(), game.getName(), game.getDeveloper(), game.getPublisher(), game.getReleaseDate(),
                game.getCategory(), game.getSagaId(), game.getSagaName(), game.getCoverImageUrl(),
                game.getReviewStatus(), game.getGenres(), game.getUpdatedAt(),
                game.getExperienceCount(), game.getTotalHours(), game.getBestRating(),
                game.getLastExperienceStatus(), game.getLastPlayedYear(), game.getHasPlatinum(), game.getHasFullCompletion(),
                game.getPlatforms(), game.getPlatformRefs(),
                titleId, psnPlatform, psnPlaytimeMinutes, lastPlayedAt);
    }
}
