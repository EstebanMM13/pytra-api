package com.estebanmm13.pytra_api.experiences.repository;

import java.math.BigDecimal;

/** One game's runs together: their hours, the exact sum of their ratings (null when none) and how many are rated. */
public record GameRunTotals(Long gameId, Double hours, BigDecimal ratingSum, Long ratedCount) {
}
