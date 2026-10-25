package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.OnlineDiaryMonthHours;
import com.estebanmm13.pytra_api.diary.repository.OnlineDiaryYearHours;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import com.estebanmm13.pytra_api.stats.dto.MonthHoursDto;
import com.estebanmm13.pytra_api.stats.dto.YearOnlineGameDto;
import com.estebanmm13.pytra_api.stats.dto.YearStatDto;
import com.estebanmm13.pytra_api.stats.dto.YearSummaryDto;
import com.estebanmm13.pytra_api.stats.repository.YearNoteRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Online hours dated by the diary take part in the per-year hours, never in the run counts. */
class StatsServiceImplYearOnlineTest {

    private static final Long USER_ID = 9L;

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final DiaryEntryGameRepository diaryEntryGameRepository = mock(DiaryEntryGameRepository.class);
    private final StatsServiceImpl service = new StatsServiceImpl(gameRepository, mock(SagaRepository.class),
            experienceRepository, mock(OnlinePlaytimeRepository.class), mock(YearNoteRepository.class),
            diaryEntryGameRepository);

    @Test
    void byYearAddsOnlineDiaryHoursAndListsOnlineOnlyYears() {
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
                run(1L, 2024, 10.0, new BigDecimal("8")),
                run(1L, 2022, 4.0, null)));
        when(diaryEntryGameRepository.sumCountedOnlineHoursByYear(USER_ID)).thenReturn(List.of(
                new OnlineDiaryYearHours(2024, new BigDecimal("3.50")),
                new OnlineDiaryYearHours(2023, new BigDecimal("2.25"))));

        List<YearStatDto> byYear = service.getByYear(USER_ID);

        assertThat(byYear).extracting(YearStatDto::getYear).containsExactly(2024, 2023, 2022);
        assertThat(byYear.get(0).getTotalHours()).isEqualTo(13.5);
        assertThat(byYear.get(0).getOnlineHours()).isEqualTo(3.5);
        assertThat(byYear.get(0).getExperienceCount()).isEqualTo(1);
        assertThat(byYear.get(0).getAverageRating()).isEqualByComparingTo("8");
        // Only online diary hours: no runs, no rating.
        assertThat(byYear.get(1).getTotalHours()).isEqualTo(2.25);
        assertThat(byYear.get(1).getExperienceCount()).isZero();
        assertThat(byYear.get(1).getAverageRating()).isNull();
        assertThat(byYear.get(2).getOnlineHours()).isZero();
    }

    @Test
    void yearsIncludeYearsWithOnlyOnlineDiaryHours() {
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of(run(1L, 2021, 1.0, null)));
        when(diaryEntryGameRepository.sumCountedOnlineHoursByYear(USER_ID)).thenReturn(List.of(
                new OnlineDiaryYearHours(2025, new BigDecimal("1")),
                new OnlineDiaryYearHours(2021, new BigDecimal("1"))));

        assertThat(service.getYears(USER_ID)).containsExactly(2025, 2021);
    }

    @Test
    void yearSummaryAddsOnlineHoursToTotalAndMonthsButNotToRunCounts() {
        when(experienceRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
                Experience.builder().id(100L).gameId(1L).runLabel("Story").status(ExperienceStatus.COMPLETADO)
                        .hours(10.0).endDate(LocalDate.of(2024, 3, 10)).build()));
        when(gameRepository.findAllByUserIdWithSagaAndGenres(USER_ID)).thenReturn(List.of(
                Game.builder().id(1L).name("Story").build(),
                Game.builder().id(2L).name("Arena").coverImageUrl("https://img.test/arena.png").build(),
                Game.builder().id(3L).name("Shooter").build()));
        when(diaryEntryGameRepository.sumCountedOnlineHoursByMonthAndGame(
                USER_ID, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31))).thenReturn(List.of(
                new OnlineDiaryMonthHours(3, 2L, new BigDecimal("2.50"), 2L),
                new OnlineDiaryMonthHours(5, 2L, new BigDecimal("1.00"), 1L),
                new OnlineDiaryMonthHours(5, 3L, new BigDecimal("6.00"), 3L)));

        YearSummaryDto summary = service.getYearSummary(USER_ID, 2024);

        assertThat(summary.getTotalHours()).isEqualTo(19.5);
        assertThat(summary.getOnlineHours()).isEqualTo(9.5);
        assertThat(summary.getExperienceCount()).isEqualTo(1);
        assertThat(summary.getCompletedCount()).isEqualTo(1);
        assertThat(summary.getExperiences()).hasSize(1);
        assertThat(summary.getMonths()).extracting(MonthHoursDto::getHours)
                .containsExactly(0.0, 0.0, 12.5, 0.0, 7.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        assertThat(summary.getHoursWithoutMonth()).isZero();

        assertThat(summary.getOnlineGames()).extracting(YearOnlineGameDto::getGameName)
                .containsExactly("Shooter", "Arena");
        YearOnlineGameDto arena = summary.getOnlineGames().get(1);
        assertThat(arena.getGameId()).isEqualTo(2L);
        assertThat(arena.getHours()).isEqualTo(3.5);
        assertThat(arena.getDayCount()).isEqualTo(3);
        assertThat(arena.getCoverImageUrl()).isEqualTo("https://img.test/arena.png");
        // Online diary hours never feed the run-based highlights.
        assertThat(summary.getMostPlayed()).noneMatch(game -> game.getGameId().equals(2L));
    }

    @Test
    void yearSummaryWithoutOnlineDiaryLinesHasNoOnlineHours() {
        YearSummaryDto summary = service.getYearSummary(USER_ID, 2024);

        verify(diaryEntryGameRepository).sumCountedOnlineHoursByMonthAndGame(
                USER_ID, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        assertThat(summary.getOnlineHours()).isZero();
        assertThat(summary.getOnlineGames()).isEmpty();
        assertThat(summary.getTotalHours()).isZero();
    }

    private static Experience run(Long gameId, int year, double hours, BigDecimal rating) {
        return Experience.builder().gameId(gameId).year(year).hours(hours).rating(rating)
                .status(ExperienceStatus.COMPLETADO).build();
    }
}
