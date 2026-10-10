package com.estebanmm13.pytra_api.experiences.service.gameStats;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.Platform;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Per-game aggregates over the game's experiences, used by the library listing.
 *
 * @param lastExperienceStatus status of the most recent run (see ExperiencePeriod#RECENCY), null without runs
 * @param lastPlayedYear       latest ExperiencePeriod#yearOf among the runs, null when none has a year
 * @param platforms            distinct platforms of the runs, in {@link Platform} enum order (empty without runs)
 * @param lastPlayedAt         latest endDate/startDate among the runs, null when none has a date
 */
public record GameExperienceStats(
        long experienceCount,
        double totalHours,
        BigDecimal bestRating,
        ExperienceStatus lastExperienceStatus,
        Integer lastPlayedYear,
        boolean hasPlatinum,
        List<Platform> platforms,
        LocalDate lastPlayedAt) {

    public static final GameExperienceStats EMPTY =
            new GameExperienceStats(0, 0.0, null, null, null, false, List.of(), null);
}
