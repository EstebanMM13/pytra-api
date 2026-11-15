package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.RunDiaryDayHours;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RunHoursTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 30);

    private static Experience run(long id, double hours, LocalDate start, LocalDate end) {
        return Experience.builder().id(id).status(end != null ? ExperienceStatus.COMPLETADO : ExperienceStatus.EN_CURSO)
                .hours(hours).startDate(start).endDate(end).build();
    }

    private static RunDiaryDayHours diary(long runId, LocalDate day, double hours) {
        return new RunDiaryDayHours(runId, day, BigDecimal.valueOf(hours));
    }

    @Test
    void spreadsTheHoursEvenlyPerDayAcrossYears() {
        Experience run = run(1, 30, LocalDate.of(2025, 12, 17), LocalDate.of(2026, 1, 15));
        RunHours hours = RunHours.of(List.of(run), List.of(), TODAY);

        assertThat(hours.inYear(run, 2025)).isCloseTo(15, within(1e-9));
        assertThat(hours.inYear(run, 2026)).isCloseTo(15, within(1e-9));
        assertThat(hours.byMonth(2026)[0]).isCloseTo(15, within(1e-9));
        assertThat(hours.byMonth(2025)[11]).isCloseTo(15, within(1e-9));
        assertThat(hours.years()).containsExactlyInAnyOrder(2025, 2026);
    }

    @Test
    void theEndedPostgameExtendsThePlayedPeriod() {
        Experience run = run(1, 19, LocalDate.of(2026, 1, 22), LocalDate.of(2026, 1, 31));
        run.setPostgameEndDate(LocalDate.of(2026, 2, 9));
        RunHours hours = RunHours.of(List.of(run), List.of(), TODAY);

        assertThat(hours.byMonth(2026)[0]).isCloseTo(10, within(1e-9));
        assertThat(hours.byMonth(2026)[1]).isCloseTo(9, within(1e-9));
    }

    @Test
    void diaryDaysKeepTheirHoursAndTheRestIsSpread() {
        Experience run = run(1, 10, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));
        RunHours hours = RunHours.of(List.of(run), List.of(diary(1, LocalDate.of(2026, 5, 2), 4)), TODAY);

        assertThat(hours.byMonth(2026)[4]).isCloseTo(4, within(1e-9));
        assertThat(hours.byMonth(2026)[2]).isCloseTo(6, within(1e-9));
        assertThat(hours.inYear(2026)).isCloseTo(10, within(1e-9));
    }

    @Test
    void diaryHoursAboveTheRunHoursAreScaledDown() {
        Experience run = run(1, 3, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));
        RunHours hours = RunHours.of(List.of(run), List.of(
                diary(1, LocalDate.of(2025, 12, 31), 4), diary(1, LocalDate.of(2026, 1, 1), 2)), TODAY);

        assertThat(hours.inYear(run, 2025)).isCloseTo(2, within(1e-9));
        assertThat(hours.inYear(run, 2026)).isCloseTo(1, within(1e-9));
    }

    @Test
    void aRunBeingPlayedIsSpreadUntilToday() {
        Experience inProgress = run(1, 61, LocalDate.of(2026, 10, 1), null);
        Experience postgame = run(2, 61, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        postgame.setPostgame(true);
        RunHours hours = RunHours.of(List.of(inProgress, postgame), List.of(), TODAY);

        assertThat(hours.byMonth(2026)[9]).isCloseTo(31 + 31 * 61.0 / 91, within(1e-9));
        assertThat(hours.inYear(inProgress, 2026)).isCloseTo(61, within(1e-9));
        assertThat(hours.inYear(postgame, 2026)).isCloseTo(61, within(1e-9));
        assertThat(hours.byMonth(2026)[8]).isCloseTo(30 * 61.0 / 91, within(1e-9));
    }

    @Test
    void oneDateIsThatDayAndNoDatesKeepTheYearField() {
        Experience onlyEnd = run(1, 5, null, LocalDate.of(2024, 7, 4));
        Experience future = run(2, 2, LocalDate.of(2027, 1, 3), null);
        Experience legacy = run(3, 8, null, null);
        legacy.setYear(2019);
        Experience undated = run(4, 9, null, null);
        Experience noHours = Experience.builder().id(5L).status(ExperienceStatus.COMPLETADO)
                .endDate(LocalDate.of(2024, 7, 4)).build();
        RunHours hours = RunHours.of(List.of(onlyEnd, future, legacy, undated, noHours), List.of(), TODAY);

        assertThat(hours.byMonth(2024)[6]).isCloseTo(5, within(1e-9));
        assertThat(hours.inYear(future, 2027)).isCloseTo(2, within(1e-9));
        assertThat(hours.inYear(legacy, 2019)).isCloseTo(8, within(1e-9));
        assertThat(hours.withoutMonth(2019)).isCloseTo(8, within(1e-9));
        assertThat(hours.years()).containsExactlyInAnyOrder(2024, 2027, 2019);
    }

    @Test
    void yearsWithLessThanAHundredthOfAnHourAreNotListed() {
        // A run of 0.001 h rounds to 0 in its year, so the year is not listed.
        Experience tiny = run(1, 0.001, LocalDate.of(2019, 6, 1), LocalDate.of(2019, 6, 1));
        RunHours hours = RunHours.of(List.of(tiny), List.of(), TODAY);

        assertThat(hours.years()).isEmpty();
        assertThat(hours.inYear(tiny, 2019)).isCloseTo(0.001, within(1e-9));
    }
}
