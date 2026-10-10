package com.estebanmm13.pytra_api.experiences.service.gameStats;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.Platform;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameExperienceStatsCalculatorTest {

    @Test
    void emptyRunsGiveEmptyStats() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(List.of());

        assertThat(stats).isEqualTo(GameExperienceStats.EMPTY);
        assertThat(stats.platforms()).isEmpty();
        assertThat(stats.lastPlayedAt()).isNull();
    }

    @Test
    void platformsAreDistinctAndInEnumOrder() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(List.of(
                run(Platform.SWITCH, null, null),
                run(Platform.PC, null, null),
                run(Platform.SWITCH, null, null),
                run(Platform.PS5, null, null)));

        assertThat(stats.platforms()).containsExactly(Platform.PC, Platform.PS5, Platform.SWITCH);
    }

    @Test
    void lastPlayedAtIsTheLatestEndOrStartDate() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(List.of(
                run(Platform.PC, LocalDate.of(2022, 1, 1), LocalDate.of(2022, 5, 1)),
                // Still running: only a start date, and it is the most recent date overall.
                run(Platform.PC, LocalDate.of(2024, 3, 10), null),
                run(Platform.PC, null, LocalDate.of(2023, 12, 31))));

        assertThat(stats.lastPlayedAt()).isEqualTo(LocalDate.of(2024, 3, 10));
    }

    @Test
    void endDateWinsOverItsOwnEarlierStartDate() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(List.of(
                run(Platform.PC, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 6, 30))));

        assertThat(stats.lastPlayedAt()).isEqualTo(LocalDate.of(2021, 6, 30));
    }

    @Test
    void lastPlayedAtIsNullWhenNoRunHasDates() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(List.of(run(Platform.XBOX, null, null)));

        assertThat(stats.lastPlayedAt()).isNull();
        assertThat(stats.platforms()).containsExactly(Platform.XBOX);
        assertThat(stats.experienceCount()).isEqualTo(1);
    }

    @Test
    void existingAggregatesAreUnchanged() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(List.of(
                Experience.builder().gameId(1L).status(ExperienceStatus.COMPLETADO).platform(Platform.PC)
                        .hours(10.0).rating(new BigDecimal("7.50")).platinum(true).year(2022).build(),
                Experience.builder().gameId(1L).status(ExperienceStatus.EN_CURSO).platform(Platform.PC)
                        .hours(2.5).platinum(false).startDate(LocalDate.of(2024, 1, 1)).build()));

        assertThat(stats.totalHours()).isEqualTo(12.5);
        assertThat(stats.bestRating()).isEqualByComparingTo("7.5");
        assertThat(stats.hasPlatinum()).isTrue();
        assertThat(stats.lastPlayedYear()).isEqualTo(2024);
    }

    private static Experience run(Platform platform, LocalDate startDate, LocalDate endDate) {
        return Experience.builder()
                .gameId(1L)
                .status(ExperienceStatus.COMPLETADO)
                .platform(platform)
                .hours(1.0)
                .platinum(false)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }
}
