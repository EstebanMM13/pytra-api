package com.estebanmm13.pytra_api.stats.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Developers of the CONFIRMED games, grouped case-insensitively on the trimmed free-text name (games
 * without a developer are left out). A game's hours are its run hours plus its online hours; a game's rating
 * is the mean of its rated runs and its online general rating (each counts once), and a developer's rating
 * is the mean of its rated games, so every game weighs the same.
 *
 * @param minRatedGames the threshold applied to {@code topByRating}
 * @param topByHours    most hours first (ties: name)
 * @param topByRating   only developers with at least {@code minRatedGames} rated games, best first
 *                      (ties: more rated games, then name)
 */
public record DeveloperStatsDto(
        int minRatedGames,
        List<DeveloperStat> topByHours,
        List<DeveloperStat> topByRating) {

    /**
     * @param developer     display spelling: the first one found (lowest game id), trimmed
     * @param averageRating 2 decimals; null when none of its games is rated
     */
    public record DeveloperStat(
            String developer,
            long gameCount,
            double hours,
            long ratedGameCount,
            BigDecimal averageRating) {
    }
}
