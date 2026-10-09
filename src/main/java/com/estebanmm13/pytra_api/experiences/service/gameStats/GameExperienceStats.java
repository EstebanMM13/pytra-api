package com.estebanmm13.pytra_api.experiences.service.gameStats;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;

/**
 * Per-game aggregates over the game's experiences, used by the library listing.
 *
 * @param lastExperienceStatus status of the most recent run (see ExperiencePeriod#RECENCY), null without runs
 * @param lastPlayedYear       latest ExperiencePeriod#yearOf among the runs, null when none has a year
 */
public record GameExperienceStats(
        long experienceCount,
        double totalHours,
        Integer bestRating,
        ExperienceStatus lastExperienceStatus,
        Integer lastPlayedYear,
        boolean hasPlatinum) {

    public static final GameExperienceStats EMPTY = new GameExperienceStats(0, 0.0, null, null, null, false);
}
