package com.estebanmm13.pytra_api.experiences.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExperiencePeriodTest {

    private static final LocalDate MARCH_2023 = LocalDate.of(2023, 3, 10);
    private static final LocalDate JUNE_2024 = LocalDate.of(2024, 6, 2);

    @Test
    void explicitYearWinsOverDates() {
        assertThat(ExperiencePeriod.yearOf(2022, MARCH_2023, JUNE_2024)).isEqualTo(2022);
    }

    @Test
    void yearFallsBackToEndDateThenStartDate() {
        assertThat(ExperiencePeriod.yearOf(null, MARCH_2023, JUNE_2024)).isEqualTo(2024);
        assertThat(ExperiencePeriod.yearOf(null, MARCH_2023, null)).isEqualTo(2023);
        assertThat(ExperiencePeriod.yearOf(null, null, null)).isNull();
    }

    @Test
    void monthComesFromEndDateWhenItFallsInTheYear() {
        assertThat(ExperiencePeriod.monthOf(2024, MARCH_2023, JUNE_2024)).isEqualTo(6);
        assertThat(ExperiencePeriod.monthOf(null, MARCH_2023, JUNE_2024)).isEqualTo(6);
    }

    @Test
    void monthFallsBackToStartDateInsideTheYear() {
        assertThat(ExperiencePeriod.monthOf(2023, MARCH_2023, JUNE_2024)).isEqualTo(3);
        assertThat(ExperiencePeriod.monthOf(null, MARCH_2023, null)).isEqualTo(3);
    }

    @Test
    void noMonthWhenNoDateFallsInTheYear() {
        assertThat(ExperiencePeriod.monthOf(2020, MARCH_2023, JUNE_2024)).isNull();
        assertThat(ExperiencePeriod.monthOf(2024, null, null)).isNull();
        assertThat(ExperiencePeriod.monthOf(null, null, null)).isNull();
    }

    @Test
    void recencyOrdersByEndThenStartThenYearAndUndatedFirst() {
        Experience undated = experience(1L, null, null, null);
        Experience yearOnly = experience(2L, 2025, null, null);
        Experience started = experience(3L, null, MARCH_2023, null);
        Experience finished = experience(4L, null, MARCH_2023, JUNE_2024);

        List<Experience> runs = new ArrayList<>(List.of(yearOnly, finished, undated, started));
        runs.sort(ExperiencePeriod.RECENCY);

        assertThat(runs).containsExactly(undated, started, finished, yearOnly);
    }

    private static Experience experience(Long id, Integer year, LocalDate start, LocalDate end) {
        return Experience.builder().id(id).year(year).startDate(start).endDate(end).build();
    }
}
