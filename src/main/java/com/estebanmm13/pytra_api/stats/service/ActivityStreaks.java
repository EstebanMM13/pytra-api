package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.stats.dto.ActivityStatsDto.Streak;

import java.time.LocalDate;
import java.util.List;

/** Streaks of consecutive play days. Input dates are distinct and sorted oldest first. */
final class ActivityStreaks {

    private ActivityStreaks() {
    }

    /** Longest run of consecutive days; on a tie the earliest one wins. */
    static Streak longest(List<LocalDate> dates) {
        Streak best = Streak.NONE;
        int start = 0;
        for (int i = 0; i < dates.size(); i++) {
            if (i > 0 && !dates.get(i - 1).plusDays(1).equals(dates.get(i))) {
                start = i;
            }
            int length = i - start + 1;
            if (length > best.length()) {
                best = new Streak(length, dates.get(start), dates.get(i));
            }
        }
        return best;
    }

    /**
     * The streak ending on the latest play day up to today, when that day is today or yesterday; otherwise
     * none. {@code today} is taken in the app's time zone ({@code AppTimeZone}). Entries dated tomorrow still
     * count (and end the streak): the diary accepts tomorrow's date, and a user travelling ahead of the app's
     * zone already logs their own today under it. Later entries are ignored.
     */
    static Streak current(List<LocalDate> dates, LocalDate today) {
        int end = dates.size() - 1;
        while (end >= 0 && dates.get(end).isAfter(today.plusDays(1))) {
            end--;
        }
        if (end < 0 || dates.get(end).isBefore(today.minusDays(1))) {
            return Streak.NONE;
        }
        int start = end;
        while (start > 0 && dates.get(start - 1).plusDays(1).equals(dates.get(start))) {
            start--;
        }
        return new Streak(end - start + 1, dates.get(start), dates.get(end));
    }
}
