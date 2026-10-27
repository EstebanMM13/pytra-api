package com.estebanmm13.pytra_api.stats.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Hours, games, runs and ratings per catalog platform, plus a per-year split of the hours for a stacked
 * chart. Hours are rounded to 2 decimals.
 *
 * <p>Online playtime rule: an online playtime's hours are split evenly across the platforms it lists (one
 * platform takes them all, two take half each, ...). Online hours of a playtime without platforms, or of a
 * game without an online playtime row, go to {@code unassignedOnlineHours} instead of any platform. In
 * all-time mode the online hours are the playtime totals; for a single year (and in {@code byYear}) they are
 * the diary hours of that year counted into online playtime (online playtime itself has no year).
 *
 * @param year                  the year filter, or null for all-time
 * @param platforms             platforms with a run or online hours in scope, most hours first
 * @param unassignedOnlineHours online hours in scope that have no platform
 * @param byYear                every year with hours, oldest first; ignores {@code year} (always all years)
 */
public record PlatformStatsDto(
        Integer year,
        List<PlatformStat> platforms,
        double unassignedOnlineHours,
        List<YearPlatforms> byYear) {

    /**
     * One platform. {@code hours = runHours + onlineHours}. {@code gameCount}: distinct games with a run on it
     * or online hours on it. {@code averageRating}: mean of its rated runs (2 decimals), null when none is
     * rated; online general ratings are left out because they are not tied to one platform.
     *
     * @param family PlatformFamily name (PLAYSTATION, XBOX, NINTENDO, PC, MOBILE, OTHER) or null
     */
    public record PlatformStat(
            Long platformId,
            String name,
            String color,
            String family,
            double hours,
            double runHours,
            double onlineHours,
            long gameCount,
            long runCount,
            long completedRunCount,
            long ratedRunCount,
            BigDecimal averageRating) {
    }

    /** One year: its hours per platform (most hours first) and the online hours without a platform. */
    public record YearPlatforms(
            int year,
            double totalHours,
            double unassignedOnlineHours,
            List<PlatformHours> platforms) {
    }

    public record PlatformHours(Long platformId, String name, String color, double hours) {
    }
}
