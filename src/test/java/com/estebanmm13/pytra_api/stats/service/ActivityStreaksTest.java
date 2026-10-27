package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.stats.dto.ActivityStatsDto.Streak;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityStreaksTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 3, 10);

    private static LocalDate day(String date) {
        return LocalDate.parse(date);
    }

    @Test
    void noDatesHaveNoStreaks() {
        assertThat(ActivityStreaks.longest(List.of())).isEqualTo(Streak.NONE);
        assertThat(ActivityStreaks.current(List.of(), TODAY)).isEqualTo(Streak.NONE);
    }

    @Test
    void longestIsTheLongestConsecutiveRunAndTheEarliestOnTies() {
        List<LocalDate> dates = List.of(
                day("2024-12-30"), day("2024-12-31"), day("2025-01-01"), // 3 days across the new year
                day("2025-01-05"),
                day("2025-02-01"), day("2025-02-02"), day("2025-02-03"));  // also 3 days, later

        assertThat(ActivityStreaks.longest(dates))
                .isEqualTo(new Streak(3, day("2024-12-30"), day("2025-01-01")));
    }

    @Test
    void singleDayIsAStreakOfOne() {
        assertThat(ActivityStreaks.longest(List.of(day("2025-01-05"))))
                .isEqualTo(new Streak(1, day("2025-01-05"), day("2025-01-05")));
    }

    @Test
    void currentStreakEndsTodayOrYesterday() {
        List<LocalDate> endingToday = List.of(day("2025-03-01"), day("2025-03-08"), day("2025-03-09"), TODAY);
        assertThat(ActivityStreaks.current(endingToday, TODAY))
                .isEqualTo(new Streak(3, day("2025-03-08"), TODAY));

        List<LocalDate> endingYesterday = List.of(day("2025-03-08"), day("2025-03-09"));
        assertThat(ActivityStreaks.current(endingYesterday, TODAY))
                .isEqualTo(new Streak(2, day("2025-03-08"), day("2025-03-09")));
    }

    @Test
    void currentStreakIsBrokenAfterAMissedDay() {
        assertThat(ActivityStreaks.current(List.of(day("2025-03-07"), day("2025-03-08")), TODAY))
                .isEqualTo(Streak.NONE);
    }

    @Test
    void futureEntriesAreIgnoredForTheCurrentStreak() {
        List<LocalDate> dates = List.of(day("2025-03-09"), TODAY, day("2025-03-20"));
        assertThat(ActivityStreaks.current(dates, TODAY)).isEqualTo(new Streak(2, day("2025-03-09"), TODAY));
    }
}
