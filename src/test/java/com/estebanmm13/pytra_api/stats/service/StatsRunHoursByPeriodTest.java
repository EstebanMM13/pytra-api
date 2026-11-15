package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.RunDiaryDayHours;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import com.estebanmm13.pytra_api.platforms.TestPlatforms;
import com.estebanmm13.pytra_api.stats.dto.MonthHoursDto;
import com.estebanmm13.pytra_api.stats.dto.MostPlayedGameDto;
import com.estebanmm13.pytra_api.stats.dto.PlatformStatsDto;
import com.estebanmm13.pytra_api.stats.dto.PlayFactsDto;
import com.estebanmm13.pytra_api.stats.dto.YearExperienceDto;
import com.estebanmm13.pytra_api.stats.dto.YearStatDto;
import com.estebanmm13.pytra_api.stats.dto.YearSummaryDto;
import com.estebanmm13.pytra_api.stats.repository.YearNoteRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Run hours follow the days they were played (diary days, the rest spread from start to credits/postgame
 * end); run counts, the year's run table and GOTY keep following the credits.
 */
class StatsRunHoursByPeriodTest {

    private static final Long USER_ID = 3L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 30);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final DiaryEntryGameRepository diaryEntryGameRepository = mock(DiaryEntryGameRepository.class);
    private final OnlinePlaytimeRepository onlinePlaytimeRepository = mock(OnlinePlaytimeRepository.class);
    private final StatsServiceImpl stats = new StatsServiceImpl(gameRepository, mock(SagaRepository.class),
            experienceRepository, onlinePlaytimeRepository, mock(YearNoteRepository.class),
            diaryEntryGameRepository, CLOCK);
    private final StatsInsightsServiceImpl insights = new StatsInsightsServiceImpl(
            diaryEntryGameRepository, experienceRepository, onlinePlaytimeRepository, gameRepository, CLOCK);

    /** 30 h from 17 Dec 2025 to the credits on 15 Jan 2026: 15 h each year, credits in 2026. */
    private final Experience acrossYears = Experience.builder().id(1L).userId(USER_ID).gameId(10L).runLabel("Run 1")
            .status(ExperienceStatus.COMPLETADO).rating(new BigDecimal("9")).hours(30.0)
            .startDate(LocalDate.of(2025, 12, 17)).endDate(LocalDate.of(2026, 1, 15))
            .platform(TestPlatforms.PS5).platinum(false).fullCompletion(false).replay(false).build();

    private void givenRuns(Experience... runs) {
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of(runs));
        when(gameRepository.findAllByUserIdWithSagaAndGenres(USER_ID)).thenReturn(List.of(
                Game.builder().id(10L).name("Saros").build()));
    }

    @Test
    void yearListSpreadsTheHoursButCountsTheRunInItsCreditsYear() {
        givenRuns(acrossYears);

        List<YearStatDto> byYear = stats.getByYear(USER_ID);

        assertThat(byYear).extracting(YearStatDto::getYear, YearStatDto::getTotalHours, YearStatDto::getExperienceCount)
                .containsExactly(tuple(2026, 15.0, 1L), tuple(2025, 15.0, 0L));
        assertThat(byYear.get(1).getAverageRating()).isNull();
        assertThat(stats.getYears(USER_ID)).containsExactly(2026, 2025);
    }

    @Test
    void yearPageShowsTheHoursPlayedThatYearAndKeepsTheRunInItsCreditsYear() {
        givenRuns(acrossYears);

        YearSummaryDto y2025 = stats.getYearSummary(USER_ID, 2025);
        YearSummaryDto y2026 = stats.getYearSummary(USER_ID, 2026);

        assertThat(y2025.getTotalHours()).isEqualTo(15.0);
        assertThat(y2025.getMonths()).extracting(MonthHoursDto::getHours).element(11).isEqualTo(15.0);
        assertThat(y2025.getExperiences()).isEmpty();
        assertThat(y2025.getMostPlayed()).extracting(MostPlayedGameDto::getGameId, MostPlayedGameDto::getTotalHours)
                .containsExactly(tuple(10L, 15.0));
        assertThat(y2025.getGoty()).isNull();

        assertThat(y2026.getTotalHours()).isEqualTo(15.0);
        assertThat(y2026.getMonths()).extracting(MonthHoursDto::getHours).element(0).isEqualTo(15.0);
        assertThat(y2026.getExperiences()).extracting(YearExperienceDto::getExperienceId, YearExperienceDto::getHours)
                .containsExactly(tuple(1L, 30.0));
        assertThat(y2026.getCompletedCount()).isEqualTo(1);
        assertThat(y2026.getGoty().getGameId()).isEqualTo(10L);
    }

    @Test
    void diaryDaysOfTheRunKeepTheirOwnMonth() {
        givenRuns(acrossYears);
        when(diaryEntryGameRepository.sumCountedRunHoursByDay(USER_ID)).thenReturn(List.of(
                new RunDiaryDayHours(1L, LocalDate.of(2026, 3, 4), new BigDecimal("6"))));

        YearSummaryDto y2026 = stats.getYearSummary(USER_ID, 2026);

        // 24 h spread over 30 days: 12 in each year; the 6 diary hours stay in March.
        assertThat(y2026.getMonths()).extracting(MonthHoursDto::getHours).element(2).isEqualTo(6.0);
        assertThat(y2026.getTotalHours()).isEqualTo(18.0);
    }

    @Test
    void platformHoursOfAYearAreTheHoursPlayedInIt() {
        givenRuns(acrossYears);

        PlatformStatsDto y2025 = insights.getPlatforms(USER_ID, 2025);
        PlatformStatsDto allTime = insights.getPlatforms(USER_ID, null);

        assertThat(y2025.platforms()).extracting(PlatformStatsDto.PlatformStat::runHours,
                        PlatformStatsDto.PlatformStat::runCount, PlatformStatsDto.PlatformStat::gameCount)
                .containsExactly(tuple(15.0, 0L, 1L));
        assertThat(allTime.platforms()).extracting(PlatformStatsDto.PlatformStat::runHours,
                        PlatformStatsDto.PlatformStat::runCount)
                .containsExactly(tuple(30.0, 1L));
        assertThat(allTime.byYear()).extracting(PlatformStatsDto.YearPlatforms::year,
                        PlatformStatsDto.YearPlatforms::totalHours)
                .containsExactly(tuple(2025, 15.0), tuple(2026, 15.0));
    }

    @Test
    void playFactsCarryTheHoursOfEveryYearPlayed() {
        givenRuns(acrossYears);

        PlayFactsDto.RunFact fact = insights.getPlayFacts(USER_ID).runs().getFirst();

        assertThat(fact.year()).isEqualTo(2026);
        assertThat(fact.hours()).isEqualTo(30.0);
        assertThat(fact.hoursByYear()).containsExactly(new PlayFactsDto.YearHours(2025, 15.0),
                new PlayFactsDto.YearHours(2026, 15.0));
    }
}
