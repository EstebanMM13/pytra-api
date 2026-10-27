package com.estebanmm13.pytra_api.stats.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Diary activity: a year of per-day totals for a heatmap, the weekday distribution of that year and the
 * all-time streaks. A day is a play day when its diary entry lists at least one game (a listed game always
 * has hours above 0); a day with only a note is not a play day.
 *
 * @param year     the year of {@code days}, {@code totalHours}, {@code activeDays} and {@code weekdays}
 * @param days     only the play days of the year, oldest first (missing dates had no play)
 * @param weekdays always seven rows, Monday (1) to Sunday (7), for the same year
 */
public record ActivityStatsDto(
        int year,
        double totalHours,
        long activeDays,
        List<Day> days,
        Streak currentStreak,
        Streak longestStreak,
        List<Weekday> weekdays) {

    /** One play day: its diary hours and the number of games played. */
    public record Day(LocalDate date, double hours, long gameCount) {
    }

    /**
     * Consecutive play days, all-time. {@code length} is 0 and both dates are null when there is none.
     * The current streak is alive while its last day is today or yesterday (today may not be logged yet).
     */
    public record Streak(int length, LocalDate startDate, LocalDate endDate) {

        public static final Streak NONE = new Streak(0, null, null);
    }

    /** {@code dayOfWeek} is ISO (1 = Monday, 7 = Sunday); {@code name} is the java.time name (MONDAY, ...). */
    public record Weekday(int dayOfWeek, String name, long daysPlayed, double hours) {
    }
}
