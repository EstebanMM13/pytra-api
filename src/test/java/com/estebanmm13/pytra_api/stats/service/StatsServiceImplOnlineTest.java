package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceTotals;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeTotals;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import com.estebanmm13.pytra_api.stats.dto.GenreStatDto;
import com.estebanmm13.pytra_api.stats.dto.TopRatedExperienceDto;
import com.estebanmm13.pytra_api.stats.repository.YearNoteRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** How online playtime takes part in the all-time stats (top rated, average, hours per genre/saga). */
class StatsServiceImplOnlineTest {

    private static final Long USER_ID = 5L;

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final OnlinePlaytimeRepository onlinePlaytimeRepository = mock(OnlinePlaytimeRepository.class);
    private final StatsServiceImpl service = new StatsServiceImpl(gameRepository, mock(SagaRepository.class),
            experienceRepository, onlinePlaytimeRepository, mock(YearNoteRepository.class));

    @Test
    void topRatedMergesRunsAndOnlineEntriesByRating() {
        when(gameRepository.findAllByUserId(USER_ID)).thenReturn(List.of(game(1L, "Story"), game(2L, "Arena")));
        when(experienceRepository.findAllByUserIdAndRatingIsNotNullOrderByRatingDesc(USER_ID)).thenReturn(List.of(
                Experience.builder().id(10L).gameId(1L).runLabel("First").rating(new BigDecimal("9.00")).build(),
                Experience.builder().id(11L).gameId(1L).runLabel("Replay").rating(new BigDecimal("7.00")).build()));
        when(onlinePlaytimeRepository.findAllByUserIdAndGeneralRatingIsNotNull(USER_ID)).thenReturn(List.of(
                OnlinePlaytime.builder().gameId(2L).totalHours(50.0).generalRating(new BigDecimal("8.50")).build()));

        List<TopRatedExperienceDto> top = service.getTopRated(USER_ID, 10);

        assertThat(top).extracting(TopRatedExperienceDto::getGameName).containsExactly("Story", "Arena", "Story");
        assertThat(top.get(0).getExperienceId()).isEqualTo(10L);
        assertThat(top.get(0).isOnline()).isFalse();
        assertThat(top.get(1).isOnline()).isTrue();
        assertThat(top.get(1).getExperienceId()).isNull();
        assertThat(top.get(1).getRunLabel()).isNull();
        assertThat(top.get(1).getRating()).isEqualByComparingTo("8.5");
    }

    @Test
    void topRatedRespectsTheLimitAfterMerging() {
        when(gameRepository.findAllByUserId(USER_ID)).thenReturn(List.of(game(1L, "Story"), game(2L, "Arena")));
        when(experienceRepository.findAllByUserIdAndRatingIsNotNullOrderByRatingDesc(USER_ID)).thenReturn(List.of(
                Experience.builder().id(10L).gameId(1L).runLabel("First").rating(new BigDecimal("6")).build()));
        when(onlinePlaytimeRepository.findAllByUserIdAndGeneralRatingIsNotNull(USER_ID)).thenReturn(List.of(
                OnlinePlaytime.builder().gameId(2L).totalHours(1.0).generalRating(new BigDecimal("10")).build()));

        assertThat(service.getTopRated(USER_ID, 1)).extracting(TopRatedExperienceDto::getGameName)
                .containsExactly("Arena");
    }

    @Test
    void averageRatingCountsOnlineRatingsLikeRuns() {
        givenRunTotals(new BigDecimal("16.00"), 2L);
        givenOnlineTotals(new BigDecimal("5.00"), 1L);

        // (16 + 5) / 3 = 7
        assertThat(service.getSummary(USER_ID).getAverageRating()).isEqualByComparingTo("7");
    }

    @Test
    void averageRatingWorksWithOnlyOnlineRatings() {
        givenRunTotals(null, 0L);
        givenOnlineTotals(new BigDecimal("8.50"), 1L);

        assertThat(service.getSummary(USER_ID).getAverageRating()).isEqualByComparingTo("8.5");
    }

    @Test
    void genreHoursIncludeOnlineHours() {
        Genre shooter = Genre.builder().id(1L).name("Shooter").build();
        Game arena = game(2L, "Arena");
        arena.setGenres(Set.of(shooter));
        when(gameRepository.findAllByUserIdWithSagaAndGenres(USER_ID)).thenReturn(List.of(arena));
        when(experienceRepository.sumHoursGroupedByGameId(USER_ID)).thenReturn(List.<Object[]>of(new Object[]{2L, 10.0}));
        when(onlinePlaytimeRepository.sumTotalHoursGroupedByGameId(USER_ID))
                .thenReturn(List.<Object[]>of(new Object[]{2L, 90.5}));

        List<GenreStatDto> genres = service.getByGenre(USER_ID);

        assertThat(genres).hasSize(1);
        assertThat(genres.get(0).getTotalHours()).isEqualTo(100.5);
    }

    private void givenRunTotals(BigDecimal ratingSum, long ratedCount) {
        ExperienceTotals totals = mock(ExperienceTotals.class);
        when(totals.getHoursSum()).thenReturn(0.0);
        when(totals.getRatingSum()).thenReturn(ratingSum);
        when(totals.getRatedCount()).thenReturn(ratedCount);
        when(experienceRepository.totalsByUserId(USER_ID)).thenReturn(totals);
    }

    private void givenOnlineTotals(BigDecimal ratingSum, long ratedCount) {
        OnlinePlaytimeTotals totals = mock(OnlinePlaytimeTotals.class);
        when(totals.getHoursSum()).thenReturn(0.0);
        when(totals.getRatingSum()).thenReturn(ratingSum);
        when(totals.getRatedCount()).thenReturn(ratedCount);
        when(onlinePlaytimeRepository.totalsByUserId(USER_ID)).thenReturn(totals);
    }

    private static Game game(Long id, String name) {
        return Game.builder().id(id).userId(USER_ID).name(name).build();
    }
}
