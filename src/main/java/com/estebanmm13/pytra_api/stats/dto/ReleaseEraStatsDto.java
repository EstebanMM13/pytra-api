package com.estebanmm13.pytra_api.stats.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The CONFIRMED games grouped by release decade and by release year, oldest first. Hours and ratings
 * per game follow {@link DeveloperStatsDto}: run plus online hours, and the mean of its rated runs and online
 * rating; a group's rating is the mean of its rated games.
 *
 * @param undatedGameCount games without a release date (in no group)
 */
public record ReleaseEraStatsDto(
        List<DecadeStat> byDecade,
        List<YearStat> byYear,
        long undatedGameCount) {

    /** {@code decade} is its first year: 1990 covers 1990-1999. */
    public record DecadeStat(int decade, long gameCount, double hours, long ratedGameCount,
                             BigDecimal averageRating) {
    }

    public record YearStat(int year, long gameCount, double hours, long ratedGameCount,
                           BigDecimal averageRating) {
    }
}
