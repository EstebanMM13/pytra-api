package com.estebanmm13.pytra_api.stats.dto;

import java.math.BigDecimal;

/**
 * One game of the all-time "best rated" ranking: every rated run of the game and its online general rating
 * count once each in {@code rating} (2 decimals, scale 10).
 *
 * @param ratedCount how many ratings were averaged (rated runs, plus 1 when the online play is rated)
 * @param hours      run hours plus online hours, 1 decimal
 */
public record TopRatedGameDto(Long gameId, String gameName, BigDecimal rating, long ratedCount, double hours) {
}
