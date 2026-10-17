package com.estebanmm13.pytra_api.experiences.repository;

import java.math.BigDecimal;

/**
 * All-time aggregates over one user's online playtimes, computed in a single query
 * ({@link OnlinePlaytimeRepository#totalsByUserId}). {@link #getRatingSum()} is null when nothing is rated.
 */
public interface OnlinePlaytimeTotals {

    Number getHoursSum();

    BigDecimal getRatingSum();

    Number getRatedCount();
}
