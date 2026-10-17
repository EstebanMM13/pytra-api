package com.estebanmm13.pytra_api.experiences.repository;

import java.math.BigDecimal;

/**
 * All-time aggregates over one user's runs, computed in a single query ({@link ExperienceRepository#totalsByUserId}).
 * Counts and hour sums are typed {@link Number} because the database returns them as Long/Double;
 * {@link #getRatingSum()} is the exact NUMERIC sum and is null when no run is rated.
 */
public interface ExperienceTotals {

    Number getExperienceCount();

    Number getHoursSum();

    Number getPlatinumCount();

    Number getReplayCount();

    Number getCompletedCount();

    Number getAbandonedCount();

    Number getInProgressCount();

    BigDecimal getRatingSum();

    Number getRatedCount();
}
