package com.estebanmm13.pytra_api.stats.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Profile of the run ratings in scope (0-10, up to 2 decimals). Online general ratings are left out: they
 * have no year and no single platform. Rating figures are exact (BigDecimal) and rounded to 2 decimals.
 *
 * @param year              the year filter, or null for all-time
 * @param bucketSize        histogram step, 0.5
 * @param ratedCount        rated runs in scope
 * @param mean              null when nothing is rated
 * @param median            mean of the two middle ratings for even samples; null when nothing is rated
 * @param standardDeviation population standard deviation; null when nothing is rated, 0 for one rating
 * @param histogram         always 21 buckets: [0, 0.5), [0.5, 1), ..., [9.5, 10) and a last [10, 10] for
 *                          perfect scores
 * @param byGenre           genres of CONFIRMED games with rated runs, best average first (ties: more ratings,
 *                          then name)
 * @param byPlatform        platforms with rated runs, best average first (ties: more ratings, then name)
 */
public record RatingStatsDto(
        Integer year,
        BigDecimal bucketSize,
        long ratedCount,
        BigDecimal mean,
        BigDecimal median,
        BigDecimal standardDeviation,
        List<RatingBucket> histogram,
        List<GenreRating> byGenre,
        List<PlatformRating> byPlatform) {

    /** Ratings with {@code from <= rating < to}, except the last bucket, where from = to = 10. */
    public record RatingBucket(BigDecimal from, BigDecimal to, long count) {
    }

    public record GenreRating(Long genreId, String genreName, long ratedCount, BigDecimal averageRating) {
    }

    public record PlatformRating(Long platformId, String name, String color, long ratedCount,
                                 BigDecimal averageRating) {
    }
}
