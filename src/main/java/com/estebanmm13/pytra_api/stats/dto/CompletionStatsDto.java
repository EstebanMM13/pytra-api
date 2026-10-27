package com.estebanmm13.pytra_api.stats.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * How runs end: time to finish, abandonment per genre, hours versus rating and replays. Runs in scope are
 * every run of the user (or those of {@code year}); the genre rows only cover CONFIRMED games.
 *
 * @param year            the year filter, or null for all-time
 * @param completedCount  COMPLETADO runs in scope
 * @param abandonedCount  ABANDONADO runs in scope
 * @param replayCount     runs in scope flagged as replay (any status)
 * @param abandonmentByGenre genres with at least one completed or abandoned run, highest rate first
 *                           (ties: bigger sample, then name); the counts let the UI hide tiny samples
 * @param hoursBuckets    COMPLETADO runs by hours: always the four buckets {@code <10}, {@code 10-30},
 *                        {@code 30-60}, {@code 60+}
 */
public record CompletionStatsDto(
        Integer year,
        long completedCount,
        long abandonedCount,
        long replayCount,
        TimeToFinish timeToFinish,
        List<GenreAbandonment> abandonmentByGenre,
        List<HoursBucket> hoursBuckets) {

    /**
     * COMPLETADO runs with a start and an end date (end not before start). Days are the calendar days
     * between both dates: a run started and finished the same day took 0 days.
     *
     * @param averageDays 1 decimal; null when {@code sampleSize} is 0
     * @param medianDays  1 decimal (mean of the two middle runs for even samples); null when empty
     * @param fastest     fewest days (ties: lowest experience id); null when empty
     * @param slowest     most days (ties: lowest experience id); null when empty
     * @param buckets     always the six buckets 0-7, 8-30, 31-90, 91-180, 181-365, 366+
     */
    public record TimeToFinish(
            long sampleSize,
            Double averageDays,
            Double medianDays,
            RunDuration fastest,
            RunDuration slowest,
            List<DaysBucket> buckets) {
    }

    public record RunDuration(Long experienceId, Long gameId, String gameName, String runLabel, long days) {
    }

    /** Runs that took {@code minDays..maxDays} days, both inclusive; {@code maxDays} null means no upper bound. */
    public record DaysBucket(String label, int minDays, Integer maxDays, long count) {
    }

    /** {@code abandonmentRate = abandoned / (completed + abandoned)}, 0..1 with 4 decimals. */
    public record GenreAbandonment(
            Long genreId,
            String genreName,
            long completedCount,
            long abandonedCount,
            double abandonmentRate) {
    }

    /**
     * Completed runs with {@code minHours <= hours < maxHours}; {@code maxHours} null means no upper bound.
     *
     * @param averageRating mean of the rated runs of the bucket, 2 decimals; null when none is rated
     */
    public record HoursBucket(
            String label,
            double minHours,
            Double maxHours,
            long runCount,
            long ratedCount,
            BigDecimal averageRating) {
    }
}
