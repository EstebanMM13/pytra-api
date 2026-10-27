package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryDayTotals;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.OnlineDiaryYearGameHours;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.platforms.TestPlatforms;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.stats.dto.ActivityStatsDto;
import com.estebanmm13.pytra_api.stats.dto.PlatformStatsDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** In-memory aggregation of the insight breakdowns, with mocked whole-user queries. */
class StatsInsightsServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2025, 6, 15);

    private final DiaryEntryGameRepository diaryEntryGameRepository = mock(DiaryEntryGameRepository.class);
    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final OnlinePlaytimeRepository onlinePlaytimeRepository = mock(OnlinePlaytimeRepository.class);
    private final StatsInsightsServiceImpl service = new StatsInsightsServiceImpl(
            diaryEntryGameRepository, experienceRepository, onlinePlaytimeRepository,
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    private final AtomicLong ids = new AtomicLong();

    private Experience run(Long gameId, Platform platform, ExperienceStatus status, String rating, double hours,
                           Integer year) {
        return Experience.builder()
                .id(ids.incrementAndGet()).userId(USER_ID).gameId(gameId).runLabel("run")
                .status(status).rating(rating != null ? new BigDecimal(rating) : null).hours(hours).year(year)
                .platform(platform).platinum(false).fullCompletion(false).replay(false)
                .build();
    }

    private static OnlinePlaytime online(Long gameId, double hours, Platform... platforms) {
        return OnlinePlaytime.builder().userId(USER_ID).gameId(gameId).totalHours(hours)
                .platforms(new java.util.HashSet<>(Set.of(platforms))).build();
    }

    // ---------------------------------------------------------------- activity

    @Test
    void activityUsesTheCurrentYearByDefault() {
        when(diaryEntryGameRepository.sumHoursByDay(USER_ID, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .thenReturn(List.of(new DiaryDayTotals(LocalDate.of(2025, 6, 14), new BigDecimal("1.25"), 1L),
                        new DiaryDayTotals(TODAY, new BigDecimal("2.50"), 2L)));
        when(diaryEntryGameRepository.findPlayedDates(USER_ID))
                .thenReturn(List.of(LocalDate.of(2025, 6, 14), TODAY));

        ActivityStatsDto activity = service.getActivity(USER_ID, null);

        assertThat(activity.year()).isEqualTo(2025);
        assertThat(activity.totalHours()).isEqualTo(3.75);
        assertThat(activity.activeDays()).isEqualTo(2);
        assertThat(activity.currentStreak()).isEqualTo(new ActivityStatsDto.Streak(2, LocalDate.of(2025, 6, 14), TODAY));
        // 2025-06-14 is a Saturday, 2025-06-15 a Sunday.
        assertThat(activity.weekdays().get(5).daysPlayed()).isEqualTo(1);
        assertThat(activity.weekdays().get(6).hours()).isEqualTo(2.5);
    }

    // ---------------------------------------------------------------- platforms

    @Test
    void platformsSplitOnlineHoursEvenlyAndKeepUnassignedApart() {
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
                run(1L, TestPlatforms.PC, ExperienceStatus.COMPLETADO, "9", 10.0, 2024),
                run(1L, TestPlatforms.PC, ExperienceStatus.ABANDONADO, "6", 2.0, 2023),
                run(2L, TestPlatforms.SWITCH, ExperienceStatus.COMPLETADO, null, 5.0, null)));
        when(onlinePlaytimeRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
                online(3L, 10.0, TestPlatforms.PC, TestPlatforms.PS5, TestPlatforms.SWITCH),
                online(4L, 7.0)));
        when(diaryEntryGameRepository.sumCountedOnlineHoursByYearAndGame(USER_ID)).thenReturn(List.of(
                new OnlineDiaryYearGameHours(2024, 3L, new BigDecimal("3")),
                new OnlineDiaryYearGameHours(2024, 4L, new BigDecimal("1.5"))));

        PlatformStatsDto allTime = service.getPlatforms(USER_ID, null);

        assertThat(allTime.unassignedOnlineHours()).isEqualTo(7.0);
        assertThat(allTime.platforms()).extracting(PlatformStatsDto.PlatformStat::name)
                .containsExactly("PC", "Switch", "PS5");
        PlatformStatsDto.PlatformStat pc = allTime.platforms().get(0);
        assertThat(pc.hours()).isEqualTo(15.33);
        assertThat(pc.runHours()).isEqualTo(12.0);
        assertThat(pc.onlineHours()).isEqualTo(3.33);
        assertThat(pc.gameCount()).isEqualTo(2);
        assertThat(pc.runCount()).isEqualTo(2);
        assertThat(pc.completedRunCount()).isEqualTo(1);
        assertThat(pc.ratedRunCount()).isEqualTo(2);
        assertThat(pc.averageRating()).isEqualByComparingTo("7.5");
        PlatformStatsDto.PlatformStat ps5 = allTime.platforms().get(2);
        assertThat(ps5.runCount()).isZero();
        assertThat(ps5.averageRating()).isNull();

        // Undated runs never reach byYear; online hours there come from the diary.
        assertThat(allTime.byYear()).extracting(PlatformStatsDto.YearPlatforms::year).containsExactly(2023, 2024);
        PlatformStatsDto.YearPlatforms y2024 = allTime.byYear().get(1);
        assertThat(y2024.totalHours()).isEqualTo(14.5);
        assertThat(y2024.unassignedOnlineHours()).isEqualTo(1.5);
        assertThat(y2024.platforms()).extracting(PlatformStatsDto.PlatformHours::hours).containsExactly(11.0, 1.0, 1.0);

        PlatformStatsDto year2024 = service.getPlatforms(USER_ID, 2024);
        assertThat(year2024.year()).isEqualTo(2024);
        assertThat(year2024.unassignedOnlineHours()).isEqualTo(1.5);
        assertThat(year2024.platforms()).extracting(PlatformStatsDto.PlatformStat::name)
                .containsExactly("PC", "PS5", "Switch");
        assertThat(year2024.platforms().get(0).hours()).isEqualTo(11.0);
        assertThat(year2024.platforms().get(0).runCount()).isEqualTo(1);
        assertThat(year2024.platforms().get(2).runCount()).isZero();
    }
}
