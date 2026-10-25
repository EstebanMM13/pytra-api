package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceTotals;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeTotals;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import com.estebanmm13.pytra_api.stats.dto.StatsSummaryDto;
import com.estebanmm13.pytra_api.stats.repository.YearNoteRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The summary reads every figure from the two aggregate rows (runs and online) plus the game and saga counts. */
class StatsServiceImplSummaryTest {

    private static final Long USER_ID = 7L;

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final SagaRepository sagaRepository = mock(SagaRepository.class);
    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final OnlinePlaytimeRepository onlinePlaytimeRepository = mock(OnlinePlaytimeRepository.class);
    private final StatsServiceImpl service = new StatsServiceImpl(gameRepository, sagaRepository,
            experienceRepository, onlinePlaytimeRepository, mock(YearNoteRepository.class));

    @Test
    void mapsEveryAggregateToItsField() {
        // 15 games, 3 of them still pending review: only the 12 confirmed ones are in the library.
        when(gameRepository.countByUserId(USER_ID)).thenReturn(15L);
        when(gameRepository.countByUserIdAndReviewStatus(USER_ID, ReviewStatus.CONFIRMED)).thenReturn(12L);
        when(sagaRepository.countByUserId(USER_ID)).thenReturn(3L);
        ExperienceTotals runs = mock(ExperienceTotals.class);
        when(runs.getExperienceCount()).thenReturn(20L);
        when(runs.getHoursSum()).thenReturn(310.5);
        when(runs.getPlatinumCount()).thenReturn(4L);
        when(runs.getFullCompletionCount()).thenReturn(3L);
        when(runs.getReplayCount()).thenReturn(2L);
        when(runs.getCompletedCount()).thenReturn(11L);
        when(runs.getAbandonedCount()).thenReturn(5L);
        when(runs.getInProgressCount()).thenReturn(1L);
        when(runs.getRatingSum()).thenReturn(new BigDecimal("15.00"));
        when(runs.getRatedCount()).thenReturn(2L);
        when(experienceRepository.totalsByUserId(USER_ID)).thenReturn(runs);
        OnlinePlaytimeTotals online = mock(OnlinePlaytimeTotals.class);
        when(online.getHoursSum()).thenReturn(99.5);
        when(online.getRatingSum()).thenReturn(new BigDecimal("6.00"));
        when(online.getRatedCount()).thenReturn(1L);
        when(onlinePlaytimeRepository.totalsByUserId(USER_ID)).thenReturn(online);

        StatsSummaryDto summary = service.getSummary(USER_ID);

        assertThat(summary.getTotalGames()).isEqualTo(12);
        assertThat(summary.getTotalSagas()).isEqualTo(3);
        assertThat(summary.getTotalExperiences()).isEqualTo(20);
        assertThat(summary.getTotalSingleplayerHours()).isEqualTo(310.5);
        assertThat(summary.getTotalOnlineHours()).isEqualTo(99.5);
        assertThat(summary.getTotalPlatinums()).isEqualTo(4);
        assertThat(summary.getTotalFullCompletions()).isEqualTo(3);
        assertThat(summary.getReplayCount()).isEqualTo(2);
        assertThat(summary.getCompletedCount()).isEqualTo(11);
        assertThat(summary.getAbandonedCount()).isEqualTo(5);
        assertThat(summary.getInProgressCount()).isEqualTo(1);
        // (15 + 6) / 3 = 7
        assertThat(summary.getAverageRating()).isEqualByComparingTo("7");
    }

    @Test
    void emptyLibraryHasZeroesAndNoAverage() {
        ExperienceTotals runs = mock(ExperienceTotals.class);
        when(runs.getExperienceCount()).thenReturn(0L);
        when(runs.getHoursSum()).thenReturn(0.0);
        when(runs.getRatedCount()).thenReturn(0L);
        when(experienceRepository.totalsByUserId(USER_ID)).thenReturn(runs);
        OnlinePlaytimeTotals online = mock(OnlinePlaytimeTotals.class);
        when(online.getHoursSum()).thenReturn(0.0);
        when(online.getRatedCount()).thenReturn(0L);
        when(onlinePlaytimeRepository.totalsByUserId(USER_ID)).thenReturn(online);

        StatsSummaryDto summary = service.getSummary(USER_ID);

        assertThat(summary.getTotalExperiences()).isZero();
        assertThat(summary.getTotalPlatinums()).isZero();
        assertThat(summary.getTotalFullCompletions()).isZero();
        assertThat(summary.getTotalSingleplayerHours()).isZero();
        assertThat(summary.getAverageRating()).isNull();
    }
}
