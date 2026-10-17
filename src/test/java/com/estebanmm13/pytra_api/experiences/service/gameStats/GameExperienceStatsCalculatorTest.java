package com.estebanmm13.pytra_api.experiences.service.gameStats;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.platforms.TestPlatforms;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    void platformsAreDistinctAndInCatalogOrder() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(List.of(
                run(TestPlatforms.DS, null, null),
                run(TestPlatforms.SWITCH, null, null),
                run(TestPlatforms.PC, null, null),
                run(TestPlatforms.SWITCH, null, null),
                run(TestPlatforms.PS5, null, null)));

        assertThat(stats.platforms()).containsExactly(
                TestPlatforms.PC, TestPlatforms.PS5, TestPlatforms.SWITCH, TestPlatforms.DS);
    }

    @Test
    void lastPlayedAtIsTheLatestEndOrStartDate() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(List.of(
                run(TestPlatforms.PC, LocalDate.of(2022, 1, 1), LocalDate.of(2022, 5, 1)),
                // Still running: only a start date, and it is the most recent date overall.
                run(TestPlatforms.PC, LocalDate.of(2024, 3, 10), null),
                run(TestPlatforms.PC, null, LocalDate.of(2023, 12, 31))));

        assertThat(stats.lastPlayedAt()).isEqualTo(LocalDate.of(2024, 3, 10));
    }

    @Test
    void endDateWinsOverItsOwnEarlierStartDate() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(List.of(
                run(TestPlatforms.PC, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 6, 30))));

        assertThat(stats.lastPlayedAt()).isEqualTo(LocalDate.of(2021, 6, 30));
    }

    @Test
    void lastPlayedAtIsNullWhenNoRunHasDates() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(List.of(run(TestPlatforms.XBOX, null, null)));

        assertThat(stats.lastPlayedAt()).isNull();
        assertThat(stats.platforms()).containsExactly(TestPlatforms.XBOX);
        assertThat(stats.experienceCount()).isEqualTo(1);
    }

    @Test
    void existingAggregatesAreUnchanged() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(List.of(
                Experience.builder().gameId(1L).status(ExperienceStatus.COMPLETADO).platform(TestPlatforms.PC)
                        .hours(10.0).rating(new BigDecimal("7.50")).platinum(true).year(2022).build(),
                Experience.builder().gameId(1L).status(ExperienceStatus.EN_CURSO).platform(TestPlatforms.PC)
                        .hours(2.5).platinum(false).startDate(LocalDate.of(2024, 1, 1)).build()));

        assertThat(stats.totalHours()).isEqualTo(12.5);
        assertThat(stats.bestRating()).isEqualByComparingTo("7.5");
        assertThat(stats.hasPlatinum()).isTrue();
        assertThat(stats.lastPlayedYear()).isEqualTo(2024);
    }

    @Test
    void onlineHoursAddToTheRunHours() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(
                List.of(run(TestPlatforms.PC, null, null), run(TestPlatforms.PC, null, null)),
                online(40.5, null, Set.of()));

        assertThat(stats.totalHours()).isEqualTo(42.5);
        assertThat(stats.experienceCount()).isEqualTo(2);
    }

    @Test
    void onlineRatingCompetesForTheBestRating() {
        Experience rated = run(TestPlatforms.PC, null, null);
        rated.setRating(new BigDecimal("7.50"));

        assertThat(GameExperienceStatsCalculator.of(List.of(rated), online(1.0, "9.25", Set.of())).bestRating())
                .isEqualByComparingTo("9.25");
        assertThat(GameExperienceStatsCalculator.of(List.of(rated), online(1.0, "6", Set.of())).bestRating())
                .isEqualByComparingTo("7.5");
        assertThat(GameExperienceStatsCalculator.of(List.of(rated), online(1.0, null, Set.of())).bestRating())
                .isEqualByComparingTo("7.5");
    }

    @Test
    void platformsAreTheDistinctUnionOfRunAndOnlinePlatformsInCatalogOrder() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(
                List.of(run(TestPlatforms.SWITCH, null, null), run(TestPlatforms.PC, null, null)),
                online(3.0, null, Set.of(TestPlatforms.DS, TestPlatforms.PC, TestPlatforms.PS5)));

        assertThat(stats.platforms()).containsExactly(
                TestPlatforms.PC, TestPlatforms.PS5, TestPlatforms.SWITCH, TestPlatforms.DS);
    }

    @Test
    void onlineOnlyGameHasHoursRatingAndPlatformsButNoRunAggregates() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(
                List.of(), online(120.0, "8", Set.of(TestPlatforms.PS5)));

        assertThat(stats.experienceCount()).isZero();
        assertThat(stats.totalHours()).isEqualTo(120.0);
        assertThat(stats.bestRating()).isEqualByComparingTo("8");
        assertThat(stats.platforms()).containsExactly(TestPlatforms.PS5);
        assertThat(stats.lastExperienceStatus()).isNull();
        assertThat(stats.lastPlayedYear()).isNull();
        assertThat(stats.lastPlayedAt()).isNull();
        assertThat(stats.hasPlatinum()).isFalse();
    }

    @Test
    void lastActivityAtIsTheLatestOfRunDatesAndTheLastOnlineSession() {
        OnlinePlaytime recentOnline = online(5.0, null, Set.of());
        recentOnline.setLastSessionAt(LocalDateTime.of(2025, 4, 2, 22, 15));
        OnlinePlaytime oldOnline = online(5.0, null, Set.of());
        oldOnline.setLastSessionAt(LocalDateTime.of(2020, 1, 1, 10, 0));
        List<Experience> runs = List.of(run(TestPlatforms.PC, LocalDate.of(2024, 3, 10), null));

        GameExperienceStats withRecentOnline = GameExperienceStatsCalculator.of(runs, recentOnline);
        assertThat(withRecentOnline.lastActivityAt()).isEqualTo(LocalDate.of(2025, 4, 2));
        // lastPlayedAt stays run-only.
        assertThat(withRecentOnline.lastPlayedAt()).isEqualTo(LocalDate.of(2024, 3, 10));
        assertThat(GameExperienceStatsCalculator.of(runs, oldOnline).lastActivityAt()).isEqualTo(LocalDate.of(2024, 3, 10));
    }

    @Test
    void lastActivityAtOfAnOnlineOnlyGameIsItsLastSession() {
        OnlinePlaytime online = online(5.0, null, Set.of());
        online.setLastSessionAt(LocalDateTime.of(2023, 7, 8, 9, 0));

        assertThat(GameExperienceStatsCalculator.of(List.of(), online).lastActivityAt()).isEqualTo(LocalDate.of(2023, 7, 8));
        assertThat(GameExperienceStatsCalculator.of(List.of(), online(5.0, null, Set.of())).lastActivityAt()).isNull();
    }

    @Test
    void lastActivityAtWithoutOnlinePlaytimeIsLastPlayedAt() {
        GameExperienceStats stats = GameExperienceStatsCalculator.of(List.of(
                run(TestPlatforms.PC, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 6, 30))));

        assertThat(stats.lastActivityAt()).isEqualTo(LocalDate.of(2021, 6, 30));
    }

    @Test
    void noRunsAndNoOnlinePlaytimeGiveEmptyStats() {
        assertThat(GameExperienceStatsCalculator.of(List.of(), null)).isEqualTo(GameExperienceStats.EMPTY);
    }

    private static OnlinePlaytime online(double hours, String rating, Set<Platform> platforms) {
        return OnlinePlaytime.builder()
                .gameId(1L)
                .totalHours(hours)
                .generalRating(rating != null ? new BigDecimal(rating) : null)
                .platforms(new HashSet<>(platforms))
                .build();
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
