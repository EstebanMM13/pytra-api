package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryDayTotals;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.OnlineDiaryYearGameHours;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.GameRunTotals;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRow;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GameGenreRow;
import com.estebanmm13.pytra_api.games.repository.GameLabel;
import com.estebanmm13.pytra_api.games.repository.GameReleaseRow;
import com.estebanmm13.pytra_api.stats.dto.CompletionStatsDto;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.stats.dto.DeveloperStatsDto;
import com.estebanmm13.pytra_api.stats.dto.RatingStatsDto;
import com.estebanmm13.pytra_api.stats.dto.ReleaseEraStatsDto;
import com.estebanmm13.pytra_api.platforms.TestPlatforms;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.stats.dto.ActivityStatsDto;
import com.estebanmm13.pytra_api.stats.dto.PlatformStatsDto;
import com.estebanmm13.pytra_api.stats.dto.PlayFactsDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** In-memory aggregation of the insight breakdowns, with mocked whole-user queries. */
class StatsInsightsServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2025, 6, 15);

    private final DiaryEntryGameRepository diaryEntryGameRepository = mock(DiaryEntryGameRepository.class);
    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final OnlinePlaytimeRepository onlinePlaytimeRepository = mock(OnlinePlaytimeRepository.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final StatsInsightsServiceImpl service = new StatsInsightsServiceImpl(
            diaryEntryGameRepository, experienceRepository, onlinePlaytimeRepository, gameRepository,
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

    @Test
    void platformsUseTheUsersOnlineBreakdownAndLeaveTheRestUnassigned() {
        OnlinePlaytime gta = online(3L, 20.0, TestPlatforms.PC, TestPlatforms.PS5, TestPlatforms.SWITCH);
        gta.getPlatformHours().putAll(Map.of(TestPlatforms.PC.getId(), 4.0, TestPlatforms.PS5.getId(), 12.0));
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(onlinePlaytimeRepository.findAllByUserId(USER_ID)).thenReturn(List.of(gta));
        when(diaryEntryGameRepository.sumCountedOnlineHoursByYearAndGame(USER_ID)).thenReturn(List.of(
                new OnlineDiaryYearGameHours(2024, 3L, new BigDecimal("10"))));

        PlatformStatsDto allTime = service.getPlatforms(USER_ID, null);

        assertThat(allTime.unassignedOnlineHours()).isEqualTo(4.0);
        assertThat(allTime.platforms()).extracting(PlatformStatsDto.PlatformStat::name, PlatformStatsDto.PlatformStat::onlineHours)
                .containsExactly(tuple("PS5", 12.0), tuple("PC", 4.0), tuple("Switch", 0.0));
        // A year's diary hours follow the same proportions.
        PlatformStatsDto.YearPlatforms y2024 = allTime.byYear().get(0);
        assertThat(y2024.unassignedOnlineHours()).isEqualTo(2.0);
        assertThat(y2024.platforms()).extracting(PlatformStatsDto.PlatformHours::name, PlatformStatsDto.PlatformHours::hours)
                .containsExactly(tuple("PS5", 6.0), tuple("PC", 2.0), tuple("Switch", 0.0));
        PlatformStatsDto year2024 = service.getPlatforms(USER_ID, 2024);
        assertThat(year2024.unassignedOnlineHours()).isEqualTo(2.0);
        assertThat(year2024.platforms()).extracting(PlatformStatsDto.PlatformStat::onlineHours).containsExactly(6.0, 2.0, 0.0);
    }

    @Test
    void anOnlineBreakdownAboveALoweredTotalIsScaledDown() {
        OnlinePlaytime lowered = online(3L, 5.0, TestPlatforms.PC, TestPlatforms.PS5);
        lowered.getPlatformHours().putAll(Map.of(TestPlatforms.PC.getId(), 4.0, TestPlatforms.PS5.getId(), 6.0));
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(onlinePlaytimeRepository.findAllByUserId(USER_ID)).thenReturn(List.of(lowered));
        when(diaryEntryGameRepository.sumCountedOnlineHoursByYearAndGame(USER_ID)).thenReturn(List.of());

        PlatformStatsDto allTime = service.getPlatforms(USER_ID, null);

        assertThat(allTime.unassignedOnlineHours()).isZero();
        assertThat(allTime.platforms()).extracting(PlatformStatsDto.PlatformStat::name, PlatformStatsDto.PlatformStat::onlineHours)
                .containsExactly(tuple("PS5", 3.0), tuple("PC", 2.0));
    }

    @Test
    void platformsForAYearStillNameThePlatformsOfOtherYearsInTheYearlyBreakdown() {
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
                run(1L, TestPlatforms.PC, ExperienceStatus.COMPLETADO, "9", 10.0, 2024),
                run(2L, TestPlatforms.SWITCH, ExperienceStatus.COMPLETADO, "7", 4.0, 2023)));
        when(onlinePlaytimeRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(diaryEntryGameRepository.sumCountedOnlineHoursByYearAndGame(USER_ID)).thenReturn(List.of());

        PlatformStatsDto year2024 = service.getPlatforms(USER_ID, 2024);

        assertThat(year2024.platforms()).extracting(PlatformStatsDto.PlatformStat::name).containsExactly("PC");
        assertThat(year2024.byYear()).extracting(PlatformStatsDto.YearPlatforms::year).containsExactly(2023, 2024);
        assertThat(year2024.byYear().get(0).platforms()).extracting(PlatformStatsDto.PlatformHours::name)
                .containsExactly("Switch");
    }

    // ---------------------------------------------------------------- developers & release eras

    private void stubGameFacts() {
        when(gameRepository.findReleaseRowsByUserIdAndReviewStatus(USER_ID, ReviewStatus.CONFIRMED)).thenReturn(List.of(
                new GameReleaseRow(1L, "FromSoftware", LocalDate.of(2011, 10, 4)),
                new GameReleaseRow(2L, "  fromsoftware ", LocalDate.of(2022, 2, 25)),
                new GameReleaseRow(3L, "Nintendo", LocalDate.of(1998, 11, 21)),
                new GameReleaseRow(4L, "   ", LocalDate.of(1998, 1, 1)),
                new GameReleaseRow(5L, "Solo Studio", null)));
        when(experienceRepository.totalsGroupedByGameId(USER_ID)).thenReturn(List.of(
                new GameRunTotals(1L, 50.0, new BigDecimal("18.00"), 2L),
                new GameRunTotals(2L, 100.0, new BigDecimal("8.00"), 1L),
                new GameRunTotals(3L, 20.0, null, 0L),
                new GameRunTotals(4L, 10.0, new BigDecimal("6.00"), 1L),
                new GameRunTotals(5L, 1.0, new BigDecimal("10.00"), 1L),
                // A pending game's runs: no CONFIRMED row, so never counted.
                new GameRunTotals(99L, 500.0, new BigDecimal("1.00"), 1L)));
        when(onlinePlaytimeRepository.findRowsByUserId(USER_ID)).thenReturn(List.of(
                new OnlinePlaytimeRow(3L, 5.0, new BigDecimal("7.00"))));
    }

    @Test
    void developersGroupCaseInsensitivelyAndApplyTheRatedGamesThreshold() {
        stubGameFacts();

        DeveloperStatsDto developers = service.getDevelopers(USER_ID, 10, 2);

        assertThat(developers.minRatedGames()).isEqualTo(2);
        assertThat(developers.topByHours()).extracting(DeveloperStatsDto.DeveloperStat::developer)
                .containsExactly("FromSoftware", "Nintendo", "Solo Studio");
        DeveloperStatsDto.DeveloperStat fromSoftware = developers.topByHours().get(0);
        assertThat(fromSoftware.gameCount()).isEqualTo(2);
        assertThat(fromSoftware.hours()).isEqualTo(150.0);
        assertThat(fromSoftware.ratedGameCount()).isEqualTo(2);
        // Per game: 9 and 8, each game weighs once.
        assertThat(fromSoftware.averageRating()).isEqualByComparingTo("8.5");
        // Run hours plus online hours; the online rating is the only one.
        assertThat(developers.topByHours().get(1).hours()).isEqualTo(25.0);
        assertThat(developers.topByHours().get(1).averageRating()).isEqualByComparingTo("7");
        assertThat(developers.topByRating()).extracting(DeveloperStatsDto.DeveloperStat::developer)
                .containsExactly("FromSoftware");

        DeveloperStatsDto anyRated = service.getDevelopers(USER_ID, 2, 0);
        assertThat(anyRated.minRatedGames()).isEqualTo(1);
        assertThat(anyRated.topByRating()).extracting(DeveloperStatsDto.DeveloperStat::developer)
                .containsExactly("Solo Studio", "FromSoftware");
        assertThat(anyRated.topByHours()).hasSize(2);
    }

    @Test
    void releaseErasGroupByDecadeAndYearAndCountUndatedGames() {
        stubGameFacts();

        ReleaseEraStatsDto eras = service.getReleaseEras(USER_ID);

        assertThat(eras.undatedGameCount()).isEqualTo(1);
        assertThat(eras.byDecade()).extracting(ReleaseEraStatsDto.DecadeStat::decade).containsExactly(1990, 2010, 2020);
        ReleaseEraStatsDto.DecadeStat nineties = eras.byDecade().get(0);
        assertThat(nineties.gameCount()).isEqualTo(2);
        assertThat(nineties.hours()).isEqualTo(35.0);
        assertThat(nineties.ratedGameCount()).isEqualTo(2);
        assertThat(nineties.averageRating()).isEqualByComparingTo("6.5");
        assertThat(eras.byYear()).extracting(ReleaseEraStatsDto.YearStat::year).containsExactly(1998, 2011, 2022);
        assertThat(eras.byYear().get(0).gameCount()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- play facts

    @Test
    void playFactsCarryEachRunsYearPlatformAndDaysToFinishAndTheOnlinePlayYears() {
        Experience timed = dated(1L, ExperienceStatus.COMPLETADO, "9", 12.5, null, "2023-12-20", "2024-01-04", false);
        Experience untimed = dated(1L, ExperienceStatus.ABANDONADO, null, 3.0, null, "2022-05-01", "2022-05-03", false);
        Experience undated = run(2L, TestPlatforms.SWITCH, ExperienceStatus.COMPLETADO, null, 0.0, null);
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of(undated, untimed, timed));
        when(onlinePlaytimeRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
                online(3L, 10.0, TestPlatforms.PS5, TestPlatforms.PC)));
        when(diaryEntryGameRepository.sumCountedOnlineHoursByYearAndGame(USER_ID)).thenReturn(List.of(
                new OnlineDiaryYearGameHours(2024, 3L, new BigDecimal("2")),
                new OnlineDiaryYearGameHours(2023, 3L, new BigDecimal("1")),
                // No hours: not a play year.
                new OnlineDiaryYearGameHours(2022, 3L, BigDecimal.ZERO),
                // Game without online playtime platforms.
                new OnlineDiaryYearGameHours(2024, 4L, new BigDecimal("1"))));

        PlayFactsDto facts = service.getPlayFacts(USER_ID);

        assertThat(facts.runs()).extracting(PlayFactsDto.RunFact::experienceId)
                .containsExactly(timed.getId(), untimed.getId(), undated.getId());
        PlayFactsDto.RunFact first = facts.runs().get(0);
        // The year follows ExperiencePeriod: the end date's year when no explicit year is set.
        assertThat(first.year()).isEqualTo(2024);
        assertThat(first.platformId()).isEqualTo(TestPlatforms.PC.getId());
        assertThat(first.hours()).isEqualTo(12.5);
        assertThat(first.daysToFinish()).isEqualTo(15L);
        // Only completed runs are timed.
        assertThat(facts.runs().get(1).daysToFinish()).isNull();
        assertThat(facts.runs().get(1).year()).isEqualTo(2022);
        assertThat(facts.runs().get(2).year()).isNull();
        assertThat(facts.runs().get(2).daysToFinish()).isNull();

        assertThat(facts.onlineYears()).containsExactly(
                new PlayFactsDto.OnlineYear(3L, 2023, List.of(TestPlatforms.PC.getId(), TestPlatforms.PS5.getId())),
                new PlayFactsDto.OnlineYear(3L, 2024, List.of(TestPlatforms.PC.getId(), TestPlatforms.PS5.getId())),
                new PlayFactsDto.OnlineYear(4L, 2024, List.of()));
    }

    // ---------------------------------------------------------------- completion habits

    private Experience dated(Long gameId, ExperienceStatus status, String rating, double hours, Integer year,
                             String start, String end, boolean replay) {
        Experience run = run(gameId, TestPlatforms.PC, status, rating, hours, year);
        run.setStartDate(start != null ? LocalDate.parse(start) : null);
        run.setEndDate(end != null ? LocalDate.parse(end) : null);
        run.setReplay(replay);
        return run;
    }

    private void stubGenres() {
        when(gameRepository.findGenreRowsByUserIdAndReviewStatus(USER_ID, ReviewStatus.CONFIRMED)).thenReturn(List.of(
                new GameGenreRow(1L, 10L, "RPG"),
                new GameGenreRow(2L, 10L, "RPG"),
                new GameGenreRow(2L, 11L, "Shooter")));
        when(gameRepository.findLabelsByUserId(USER_ID)).thenReturn(List.of(
                new GameLabel(1L, "Quick Game", null), new GameLabel(2L, "Long Game", null)));
    }

    @Test
    void completionMeasuresTimeToFinishAbandonmentAndHoursBuckets() {
        Experience sameDay = dated(1L, ExperienceStatus.COMPLETADO, "9", 5.0, 2024, "2024-01-01", "2024-01-01", false);
        Experience twoMonths = dated(2L, ExperienceStatus.COMPLETADO, "7", 40.0, 2024, "2024-01-01", "2024-03-01", true);
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
                sameDay,
                twoMonths,
                dated(1L, ExperienceStatus.COMPLETADO, null, 12.0, 2024, "2024-02-01", "2024-02-11", false),
                dated(2L, ExperienceStatus.ABANDONADO, null, 3.0, 2024, null, null, false),
                dated(3L, ExperienceStatus.ABANDONADO, null, 1.0, 2024, null, null, false),
                // No end date: completed but not timed.
                dated(1L, ExperienceStatus.COMPLETADO, "10", 100.0, 2023, "2023-01-01", null, false),
                dated(2L, ExperienceStatus.EN_CURSO, null, 1.0, 2024, null, null, true),
                // End before start: ignored by the time to finish.
                dated(1L, ExperienceStatus.COMPLETADO, null, 0.0, 2022, "2022-05-01", "2022-04-01", false)));
        stubGenres();

        CompletionStatsDto completion = service.getCompletion(USER_ID, null);

        assertThat(completion.year()).isNull();
        assertThat(completion.completedCount()).isEqualTo(5);
        assertThat(completion.abandonedCount()).isEqualTo(2);
        assertThat(completion.replayCount()).isEqualTo(2);

        CompletionStatsDto.TimeToFinish time = completion.timeToFinish();
        assertThat(time.sampleSize()).isEqualTo(3);
        assertThat(time.averageDays()).isEqualTo(23.3);
        assertThat(time.medianDays()).isEqualTo(10.0);
        assertThat(time.fastest().experienceId()).isEqualTo(sameDay.getId());
        assertThat(time.fastest().gameName()).isEqualTo("Quick Game");
        assertThat(time.fastest().days()).isZero();
        assertThat(time.slowest().experienceId()).isEqualTo(twoMonths.getId());
        assertThat(time.slowest().days()).isEqualTo(60);
        assertThat(time.buckets()).extracting(CompletionStatsDto.DaysBucket::count).containsExactly(1L, 1L, 1L, 0L, 0L, 0L);
        assertThat(time.buckets()).extracting(CompletionStatsDto.DaysBucket::gameCount).containsExactly(1L, 1L, 1L, 0L, 0L, 0L);
        assertThat(time.buckets().getLast().maxDays()).isNull();

        // RPG: 5 completed, 1 abandoned; Shooter: 1 and 1.
        assertThat(completion.abandonmentByGenre()).extracting(CompletionStatsDto.GenreAbandonment::genreName)
                .containsExactly("Shooter", "RPG");
        assertThat(completion.abandonmentByGenre().get(0).abandonmentRate()).isEqualTo(0.5);
        assertThat(completion.abandonmentByGenre().get(1).completedCount()).isEqualTo(5);
        assertThat(completion.abandonmentByGenre().get(1).abandonmentRate()).isEqualTo(0.1667);

        assertThat(completion.hoursBuckets()).extracting(CompletionStatsDto.HoursBucket::label)
                .containsExactly("<10", "10-30", "30-60", "60+");
        assertThat(completion.hoursBuckets()).extracting(CompletionStatsDto.HoursBucket::runCount)
                .containsExactly(2L, 1L, 1L, 1L);
        // The two runs under 10 h are of the same game.
        assertThat(completion.hoursBuckets()).extracting(CompletionStatsDto.HoursBucket::gameCount)
                .containsExactly(1L, 1L, 1L, 1L);
        assertThat(completion.hoursBuckets().get(0).averageRating()).isEqualByComparingTo("9");
        assertThat(completion.hoursBuckets().get(1).ratedCount()).isZero();
        assertThat(completion.hoursBuckets().get(1).averageRating()).isNull();
        assertThat(completion.hoursBuckets().get(3).maxHours()).isNull();
    }

    @Test
    void completionOfAYearWithoutTimedRunsHasEmptyTimeToFinish() {
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
                dated(1L, ExperienceStatus.COMPLETADO, "10", 100.0, 2023, "2023-01-01", null, false),
                dated(1L, ExperienceStatus.COMPLETADO, "8", 1.0, 2024, "2024-01-01", "2024-01-03", false)));
        stubGenres();

        CompletionStatsDto completion = service.getCompletion(USER_ID, 2023);

        assertThat(completion.year()).isEqualTo(2023);
        assertThat(completion.completedCount()).isEqualTo(1);
        assertThat(completion.timeToFinish().sampleSize()).isZero();
        assertThat(completion.timeToFinish().averageDays()).isNull();
        assertThat(completion.timeToFinish().fastest()).isNull();
        assertThat(completion.timeToFinish().buckets()).hasSize(6);
    }

    // ---------------------------------------------------------------- rating profile

    @Test
    void ratingBucketsAreHalfPointsWithPerfectScoresApart() {
        assertThat(StatsInsightsServiceImpl.ratingBucketOf(new BigDecimal("0"))).isZero();
        assertThat(StatsInsightsServiceImpl.ratingBucketOf(new BigDecimal("0.49"))).isZero();
        assertThat(StatsInsightsServiceImpl.ratingBucketOf(new BigDecimal("0.5"))).isEqualTo(1);
        assertThat(StatsInsightsServiceImpl.ratingBucketOf(new BigDecimal("9.99"))).isEqualTo(19);
        assertThat(StatsInsightsServiceImpl.ratingBucketOf(new BigDecimal("10.00"))).isEqualTo(20);
    }

    @Test
    void ratingsProfileRatedRunsByGenreAndPlatform() {
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
                run(1L, TestPlatforms.PC, ExperienceStatus.COMPLETADO, "10", 1.0, 2024),
                run(1L, TestPlatforms.PC, ExperienceStatus.COMPLETADO, "8.5", 1.0, 2024),
                run(2L, TestPlatforms.PS5, ExperienceStatus.ABANDONADO, "6", 1.0, 2024),
                run(2L, TestPlatforms.PS5, ExperienceStatus.COMPLETADO, "7.25", 1.0, 2023),
                run(3L, TestPlatforms.SWITCH, ExperienceStatus.COMPLETADO, null, 1.0, 2024)));
        stubGenres();

        RatingStatsDto ratings = service.getRatings(USER_ID, null);

        assertThat(ratings.year()).isNull();
        assertThat(ratings.bucketSize()).isEqualByComparingTo("0.5");
        assertThat(ratings.ratedCount()).isEqualTo(4);
        assertThat(ratings.mean()).isEqualByComparingTo("7.94");
        assertThat(ratings.median()).isEqualByComparingTo("7.88");
        assertThat(ratings.standardDeviation()).isEqualByComparingTo("1.48");
        assertThat(ratings.histogram()).hasSize(21);
        assertThat(ratings.histogram().get(12).from()).isEqualByComparingTo("6");
        assertThat(ratings.histogram().get(12).to()).isEqualByComparingTo("6.5");
        assertThat(ratings.histogram().get(12).count()).isEqualTo(1);
        assertThat(ratings.histogram().get(14).count()).isEqualTo(1);
        assertThat(ratings.histogram().get(17).count()).isEqualTo(1);
        assertThat(ratings.histogram().get(20).from()).isEqualByComparingTo("10");
        assertThat(ratings.histogram().get(20).to()).isEqualByComparingTo("10");
        assertThat(ratings.histogram().get(20).count()).isEqualTo(1);
        assertThat(ratings.histogram()).extracting(RatingStatsDto.RatingBucket::count)
                .containsOnly(0L, 1L);

        // RPG: games 1 and 2 (all four ratings); Shooter: game 2 only.
        assertThat(ratings.byGenre()).extracting(RatingStatsDto.GenreRating::genreName).containsExactly("RPG", "Shooter");
        assertThat(ratings.byGenre().get(0).ratedCount()).isEqualTo(4);
        assertThat(ratings.byGenre().get(1).averageRating()).isEqualByComparingTo("6.63");
        assertThat(ratings.byPlatform()).extracting(RatingStatsDto.PlatformRating::name).containsExactly("PC", "PS5");
        assertThat(ratings.byPlatform().get(0).averageRating()).isEqualByComparingTo("9.25");
        assertThat(ratings.byPlatform().get(1).ratedCount()).isEqualTo(2);

        RatingStatsDto year2023 = service.getRatings(USER_ID, 2023);
        assertThat(year2023.ratedCount()).isEqualTo(1);
        assertThat(year2023.standardDeviation()).isEqualByComparingTo("0");
    }

    @Test
    void ratingsOfNothingRatedAreNull() {
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        RatingStatsDto ratings = service.getRatings(USER_ID, null);

        assertThat(ratings.ratedCount()).isZero();
        assertThat(ratings.mean()).isNull();
        assertThat(ratings.median()).isNull();
        assertThat(ratings.standardDeviation()).isNull();
        assertThat(ratings.histogram()).hasSize(21).allMatch(bucket -> bucket.count() == 0);
        assertThat(ratings.byGenre()).isEmpty();
        assertThat(ratings.byPlatform()).isEmpty();
    }
}
