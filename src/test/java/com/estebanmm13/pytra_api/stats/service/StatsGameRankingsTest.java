package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.GameDiaryDays;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.GameRunTotals;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRow;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GameLabel;
import com.estebanmm13.pytra_api.games.repository.GameReleaseRow;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.stats.dto.GameRankingsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The game rankings of the stats "Games" screen, with mocked whole-user queries. */
class StatsGameRankingsTest {

    private static final Long USER_ID = 7L;
    /** Game 9 exists but is a pending import (not CONFIRMED): it never appears in a ranking. */
    private static final Long UNCONFIRMED = 9L;

    private final DiaryEntryGameRepository diaryEntryGameRepository = mock(DiaryEntryGameRepository.class);
    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final OnlinePlaytimeRepository onlinePlaytimeRepository = mock(OnlinePlaytimeRepository.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final StatsInsightsServiceImpl service = new StatsInsightsServiceImpl(
            diaryEntryGameRepository, experienceRepository, onlinePlaytimeRepository, gameRepository,
            Clock.fixed(LocalDate.of(2026, 11, 1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    private final AtomicLong ids = new AtomicLong();
    private final List<Experience> runs = new ArrayList<>();
    private final List<GameRunTotals> runTotals = new ArrayList<>();
    private final List<OnlinePlaytimeRow> onlineRows = new ArrayList<>();
    private final List<GameDiaryDays> diaryDays = new ArrayList<>();

    @BeforeEach
    void stubQueries() {
        when(gameRepository.findReleaseRowsByUserIdAndReviewStatus(USER_ID, ReviewStatus.CONFIRMED)).thenReturn(List.of(
                new GameReleaseRow(1L, null, null, null), new GameReleaseRow(2L, null, null, null),
                new GameReleaseRow(3L, null, null, null), new GameReleaseRow(4L, null, null, null)));
        when(gameRepository.findLabelsByUserId(USER_ID)).thenReturn(List.of(
                new GameLabel(1L, "Alpha", null), new GameLabel(2L, "Bravo", null),
                new GameLabel(3L, "Charlie", null), new GameLabel(4L, "Delta", null),
                new GameLabel(UNCONFIRMED, "Imported", null)));
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(runs);
        when(experienceRepository.totalsGroupedByGameId(USER_ID)).thenReturn(runTotals);
        when(onlinePlaytimeRepository.findRowsByUserId(USER_ID)).thenReturn(onlineRows);
        when(diaryEntryGameRepository.countPlayedDaysByGame(USER_ID)).thenReturn(diaryDays);
    }

    private Experience run(Long gameId, ExperienceStatus status, double hours) {
        Experience run = Experience.builder()
                .id(ids.incrementAndGet()).userId(USER_ID).gameId(gameId).runLabel("run " + ids.get())
                .status(status).hours(hours).platinum(false).fullCompletion(false).replay(false)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
        runs.add(run);
        return run;
    }

    // ---------------------------------------------------------------- most replayed

    @Test
    void mostReplayedCountsRunsThatAreNotPendingOfGamesWithTwoOrMore() {
        run(1L, ExperienceStatus.COMPLETADO, 10);
        run(1L, ExperienceStatus.COMPLETADO, 12);
        run(1L, ExperienceStatus.PENDIENTE, 0);
        run(2L, ExperienceStatus.COMPLETADO, 5);
        run(2L, ExperienceStatus.ABANDONADO, 2);
        run(2L, ExperienceStatus.EN_CURSO, 1);
        // One counted run plus a pending one: not a replayed game.
        run(3L, ExperienceStatus.COMPLETADO, 40);
        run(3L, ExperienceStatus.PENDIENTE, 0);
        // Same run count as game 1 but fewer hours: after it.
        run(4L, ExperienceStatus.COMPLETADO, 3);
        run(4L, ExperienceStatus.COMPLETADO, 3);
        run(UNCONFIRMED, ExperienceStatus.COMPLETADO, 1);
        run(UNCONFIRMED, ExperienceStatus.COMPLETADO, 1);
        run(UNCONFIRMED, ExperienceStatus.COMPLETADO, 1);

        GameRankingsDto rankings = service.getGameRankings(USER_ID, 50);

        assertThat(rankings.mostReplayed())
                .extracting(GameRankingsDto.ReplayedGame::gameName, GameRankingsDto.ReplayedGame::runCount,
                        GameRankingsDto.ReplayedGame::hours)
                .containsExactly(tuple("Bravo", 3L, 8.0), tuple("Alpha", 2L, 22.0), tuple("Delta", 2L, 6.0));
    }

    // ---------------------------------------------------------------- longest runs

    @Test
    void longestRunsRankSingleRunsByTheirOwnHours() {
        Experience long1 = run(1L, ExperienceStatus.COMPLETADO, 120);
        Experience tieFirst = run(2L, ExperienceStatus.ABANDONADO, 30);
        Experience tieSecond = run(1L, ExperienceStatus.EN_CURSO, 30);
        run(3L, ExperienceStatus.PENDIENTE, 50);
        run(4L, ExperienceStatus.COMPLETADO, 0);
        run(UNCONFIRMED, ExperienceStatus.COMPLETADO, 500);

        GameRankingsDto rankings = service.getGameRankings(USER_ID, 50);

        assertThat(rankings.longestRuns())
                .extracting(GameRankingsDto.LongRun::experienceId, GameRankingsDto.LongRun::gameName,
                        GameRankingsDto.LongRun::runLabel, GameRankingsDto.LongRun::hours)
                .containsExactly(
                        tuple(long1.getId(), "Alpha", long1.getRunLabel(), 120.0),
                        tuple(tieFirst.getId(), "Bravo", tieFirst.getRunLabel(), 30.0),
                        tuple(tieSecond.getId(), "Alpha", tieSecond.getRunLabel(), 30.0));
    }

    // ---------------------------------------------------------------- short gems

    @Test
    void shortGemsAreHighlyRatedGamesWithFewHours() {
        // Alpha: 9.5 over 12 h. Bravo: 8 (mean of 7 and 9) over 10 h run + 5 h online = 15 h, the limit.
        runTotals.add(new GameRunTotals(1L, 12.0, new BigDecimal("9.5"), 1L));
        runTotals.add(new GameRunTotals(2L, 10.0, new BigDecimal("7"), 1L));
        onlineRows.add(new OnlinePlaytimeRow(2L, 5.0, new BigDecimal("9")));
        // Charlie: rated 9 but 15.5 h. Delta: 7.99 is under the bar.
        runTotals.add(new GameRunTotals(3L, 15.5, new BigDecimal("9"), 1L));
        runTotals.add(new GameRunTotals(4L, 3.0, new BigDecimal("7.99"), 1L));
        runTotals.add(new GameRunTotals(UNCONFIRMED, 1.0, new BigDecimal("10"), 1L));

        GameRankingsDto rankings = service.getGameRankings(USER_ID, 50);

        assertThat(rankings.shortGems())
                .extracting(GameRankingsDto.ShortGem::gameName, GameRankingsDto.ShortGem::rating,
                        GameRankingsDto.ShortGem::hours)
                .containsExactly(tuple("Alpha", new BigDecimal("9.50"), 12.0), tuple("Bravo", new BigDecimal("8.00"), 15.0));
    }

    @Test
    void shortGemsLeaveOutGamesWithoutHours() {
        runTotals.add(new GameRunTotals(1L, 0.0, new BigDecimal("10"), 1L));

        assertThat(service.getGameRankings(USER_ID, 50).shortGems()).isEmpty();
    }

    // ---------------------------------------------------------------- most days played

    @Test
    void mostDaysPlayedRanksGamesByDistinctDiaryDays() {
        diaryDays.add(new GameDiaryDays(1L, 4L));
        diaryDays.add(new GameDiaryDays(3L, 9L));
        diaryDays.add(new GameDiaryDays(2L, 4L));
        diaryDays.add(new GameDiaryDays(UNCONFIRMED, 30L));

        GameRankingsDto rankings = service.getGameRankings(USER_ID, 50);

        assertThat(rankings.mostDaysPlayed())
                .extracting(GameRankingsDto.DaysPlayedGame::gameName, GameRankingsDto.DaysPlayedGame::days)
                .containsExactly(tuple("Charlie", 9L), tuple("Alpha", 4L), tuple("Bravo", 4L));
    }

    // ---------------------------------------------------------------- longest pending

    @Test
    void longestPendingListsGamesWhoseLatestRunIsPendingOldestFirst() {
        Experience recent = run(1L, ExperienceStatus.PENDIENTE, 0);
        recent.setCreatedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        Experience oldest = run(2L, ExperienceStatus.PENDIENTE, 0);
        oldest.setCreatedAt(LocalDateTime.of(2024, 3, 5, 8, 0));
        // Same date as game 1 (estimated rows share one): the lower id goes first, so game 1 stays ahead.
        Experience sameDate = run(4L, ExperienceStatus.PENDIENTE, 0);
        sameDate.setCreatedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        // Game 3's latest run is completed (it has dates; a dateless run counts as the oldest): not pending.
        run(3L, ExperienceStatus.PENDIENTE, 0);
        Experience done = run(3L, ExperienceStatus.COMPLETADO, 20);
        done.setEndDate(LocalDate.of(2025, 1, 1));
        run(UNCONFIRMED, ExperienceStatus.PENDIENTE, 0).setCreatedAt(LocalDateTime.of(2020, 1, 1, 0, 0));

        GameRankingsDto rankings = service.getGameRankings(USER_ID, 50);

        assertThat(rankings.longestPending())
                .extracting(GameRankingsDto.PendingGame::gameName, GameRankingsDto.PendingGame::experienceId,
                        GameRankingsDto.PendingGame::pendingSince)
                .containsExactly(
                        tuple("Bravo", oldest.getId(), LocalDateTime.of(2024, 3, 5, 8, 0)),
                        tuple("Alpha", recent.getId(), LocalDateTime.of(2026, 6, 1, 10, 0)),
                        tuple("Delta", sameDate.getId(), LocalDateTime.of(2026, 6, 1, 10, 0)));
    }

    // ---------------------------------------------------------------- limit

    @Test
    void everyListIsCappedAtTheClampedLimit() {
        for (long game = 1; game <= 4; game++) {
            run(game, ExperienceStatus.COMPLETADO, 10 + game);
            run(game, ExperienceStatus.COMPLETADO, 1);
            run(game, ExperienceStatus.PENDIENTE, 0).setStartDate(LocalDate.of(2027, 1, 1));
            diaryDays.add(new GameDiaryDays(game, game));
            runTotals.add(new GameRunTotals(game, 5.0, new BigDecimal("9"), 1L));
        }

        GameRankingsDto two = service.getGameRankings(USER_ID, 2);
        assertThat(two.mostReplayed()).hasSize(2);
        assertThat(two.longestRuns()).hasSize(2);
        assertThat(two.shortGems()).hasSize(2);
        assertThat(two.mostDaysPlayed()).hasSize(2);
        assertThat(two.longestPending()).hasSize(2);

        // Below 1 means 1, like the other stats lists.
        assertThat(service.getGameRankings(USER_ID, 0).mostDaysPlayed()).hasSize(1);
    }
}
