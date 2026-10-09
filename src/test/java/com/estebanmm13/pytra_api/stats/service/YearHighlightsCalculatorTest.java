package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.Platform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.model.Saga;
import com.estebanmm13.pytra_api.stats.dto.MostPlayedGameDto;
import com.estebanmm13.pytra_api.stats.dto.RatedGameBriefDto;
import com.estebanmm13.pytra_api.stats.dto.RatedGameDto;
import com.estebanmm13.pytra_api.stats.service.YearHighlightsCalculator.YearHighlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.estebanmm13.pytra_api.experiences.model.ExperienceStatus.ABANDONADO;
import static com.estebanmm13.pytra_api.experiences.model.ExperienceStatus.COMPLETADO;
import static com.estebanmm13.pytra_api.experiences.model.ExperienceStatus.EN_CURSO;
import static org.assertj.core.api.Assertions.assertThat;

class YearHighlightsCalculatorTest {

    private static final int YEAR = 2024;
    private static final LocalDate IN_YEAR = LocalDate.of(YEAR, 5, 1);
    private static final LocalDate PREVIOUS_YEAR = LocalDate.of(YEAR - 1, 12, 31);

    private final Map<Long, Game> games = new HashMap<>();
    private final List<Experience> experiences = new ArrayList<>();
    private long nextExperienceId = 1;

    @BeforeEach
    void reset() {
        games.clear();
        experiences.clear();
    }

    @Test
    void completedInYearRequiresCompletadoAndEndDateInTheYear() {
        game(1, "Counted");
        game(2, "Still playing");
        game(3, "Finished last year");
        game(4, "No end date");
        run(1, COMPLETADO, 9, 10.0, IN_YEAR, null);
        run(2, EN_CURSO, 9, 10.0, IN_YEAR, YEAR);
        run(3, COMPLETADO, 9, 10.0, PREVIOUS_YEAR, YEAR);
        run(4, COMPLETADO, 9, 10.0, null, YEAR);

        YearHighlights highlights = compute();

        assertThat(highlights.topRated()).extracting(RatedGameDto::getGameId).containsExactly(1L);
        assertThat(highlights.goty().getGameId()).isEqualTo(1L);
    }

    @Test
    void mostPlayedUsesTheYearFieldOnlyWhateverTheStatus() {
        game(1, "Year field");
        game(2, "Only end date");
        run(1, ABANDONADO, null, 30.0, null, YEAR);
        run(1, EN_CURSO, null, 5.0, null, YEAR);
        run(2, COMPLETADO, 9, 100.0, IN_YEAR, YEAR - 1);

        YearHighlights highlights = compute();

        assertThat(highlights.mostPlayed()).hasSize(1);
        MostPlayedGameDto top = highlights.mostPlayed().getFirst();
        assertThat(top.getGameId()).isEqualTo(1L);
        assertThat(top.getTotalHours()).isEqualTo(35.0);
        // The completed run still drives the rating-based highlights.
        assertThat(highlights.goty().getGameId()).isEqualTo(2L);
    }

    @Test
    void mostPlayedIsTopFiveByHoursWithNameTieBreak() {
        for (long id = 1; id <= 6; id++) {
            game(id, "Game " + id);
            run(id, EN_CURSO, null, id == 6 ? 50.0 : 10.0, null, YEAR);
        }

        assertThat(compute().mostPlayed()).extracting(MostPlayedGameDto::getGameName)
                .containsExactly("Game 6", "Game 1", "Game 2", "Game 3", "Game 4");
    }

    @Test
    void gotyIsTheBestAverageAndCountsIncludeUnratedCompletedRuns() {
        game(1, "Great");
        game(2, "Good");
        run(1, COMPLETADO, 10, 20.0, IN_YEAR, null);
        run(1, COMPLETADO, 8, 10.0, IN_YEAR, null);
        run(1, COMPLETADO, null, 5.0, IN_YEAR, null);
        run(2, COMPLETADO, 8, 40.0, IN_YEAR, null);

        RatedGameDto goty = compute().goty();

        assertThat(goty.getGameId()).isEqualTo(1L);
        assertThat(goty.getGameName()).isEqualTo("Great");
        assertThat(goty.getAvgRating()).isEqualByComparingTo("9");
        assertThat(goty.getExperienceCount()).isEqualTo(3);
        assertThat(goty.getTotalHours()).isEqualTo(35.0);
    }

    @Test
    void noHighlightsWithoutData() {
        YearHighlights highlights = compute();

        assertThat(highlights.goty()).isNull();
        assertThat(highlights.topRated()).isEmpty();
        assertThat(highlights.mostPlayed()).isEmpty();
        assertThat(highlights.topSagas()).isEmpty();
        assertThat(highlights.topGenres()).isEmpty();
        assertThat(highlights.surprises()).isEmpty();
        assertThat(highlights.disappointments()).isEmpty();
    }

    @Test
    void gamesWithoutRatedCompletedRunsAreNotRanked() {
        game(1, "Unrated");
        run(1, COMPLETADO, null, 10.0, IN_YEAR, null);

        assertThat(compute().goty()).isNull();
    }

    @Test
    void topRatedIsTopFiveAndTiesBreakByHoursThenName() {
        game(1, "Beta");
        game(2, "Alpha");
        game(3, "Many hours");
        for (long id = 4; id <= 7; id++) {
            game(id, "Low " + id);
            run(id, COMPLETADO, 5, 1.0, IN_YEAR, null);
        }
        run(1, COMPLETADO, 9, 10.0, IN_YEAR, null);
        run(2, COMPLETADO, 9, 10.0, IN_YEAR, null);
        run(3, COMPLETADO, 9, 50.0, IN_YEAR, null);

        assertThat(compute().topRated()).extracting(RatedGameDto::getGameName)
                .containsExactly("Many hours", "Alpha", "Beta", "Low 4", "Low 5");
    }

    @Test
    void surprisesAreInclusiveAtEightAndAHalfAndExcludeTheGoty() {
        game(1, "Goty");
        game(2, "Exactly 8.5");
        game(3, "Just below");
        game(4, "Nine");
        game(5, "Nine too");
        game(6, "Nine as well");
        run(1, COMPLETADO, 10, 1.0, IN_YEAR, null);
        run(2, COMPLETADO, 8, 1.0, IN_YEAR, null);
        run(2, COMPLETADO, 9, 1.0, IN_YEAR, null);
        run(3, COMPLETADO, 8, 1.0, IN_YEAR, null);
        run(4, COMPLETADO, 9, 4.0, IN_YEAR, null);
        run(5, COMPLETADO, 9, 3.0, IN_YEAR, null);
        run(6, COMPLETADO, 9, 2.0, IN_YEAR, null);

        YearHighlights highlights = compute();

        assertThat(highlights.goty().getGameId()).isEqualTo(1L);
        assertThat(highlights.surprises()).extracting(RatedGameBriefDto::getGameName)
                .containsExactly("Nine", "Nine too", "Nine as well");

        // With fewer 9s, the inclusive 8.5 game makes the cut.
        experiences.removeIf(e -> e.getGameId() == 5L || e.getGameId() == 6L);
        assertThat(compute().surprises()).extracting(RatedGameBriefDto::getGameName)
                .containsExactly("Nine", "Exactly 8.5");
    }

    @Test
    void disappointmentsAreInclusiveAtSixWorstFirstAndCappedAtThree() {
        game(1, "Six");
        game(2, "Six point five");
        game(3, "Two");
        game(4, "Four");
        game(5, "Five");
        run(1, COMPLETADO, 6, 1.0, IN_YEAR, null);
        run(2, COMPLETADO, 6, 1.0, IN_YEAR, null);
        run(2, COMPLETADO, 7, 1.0, IN_YEAR, null);
        run(3, COMPLETADO, 2, 1.0, IN_YEAR, null);
        run(4, COMPLETADO, 4, 1.0, IN_YEAR, null);

        assertThat(compute().disappointments()).extracting(RatedGameBriefDto::getGameName)
                .containsExactly("Two", "Four", "Six");

        experiences.removeIf(e -> e.getGameId() == 3L);
        assertThat(compute().disappointments()).extracting(RatedGameBriefDto::getAvgRating)
                .containsExactly(new BigDecimal("4"), new BigDecimal("6"));
    }

    @Test
    void decimalRatingsAreAveragedExactlyAndThresholdsStayInclusive() {
        game(1, "Goty");
        game(2, "Exactly 8.5");
        game(3, "Rounds up to 8.5");
        game(4, "Exactly 6");
        game(5, "Just above 6");
        decimalRun(1, COMPLETADO, "9.75", 1.0, IN_YEAR, null);
        decimalRun(2, COMPLETADO, "8.50", 1.0, IN_YEAR, null);
        decimalRun(3, COMPLETADO, "8.75", 1.0, IN_YEAR, null);
        decimalRun(3, COMPLETADO, "8.24", 1.0, IN_YEAR, null); // (8.75 + 8.24) / 2 = 8.495 -> 8.50 half up
        decimalRun(4, COMPLETADO, "5.50", 1.0, IN_YEAR, null);
        decimalRun(4, COMPLETADO, "6.50", 1.0, IN_YEAR, null);
        decimalRun(5, COMPLETADO, "6.01", 1.0, IN_YEAR, null);

        YearHighlights highlights = compute();

        assertThat(highlights.goty().getAvgRating()).isEqualByComparingTo("9.75");
        assertThat(highlights.surprises()).extracting(RatedGameBriefDto::getGameName)
                .containsExactly("Rounds up to 8.5", "Exactly 8.5");
        assertThat(highlights.disappointments()).extracting(RatedGameBriefDto::getGameName)
                .containsExactly("Exactly 6");
        assertThat(highlights.disappointments().getFirst().getAvgRating().toPlainString()).isEqualTo("6");

        // 8.49 average (rounded) misses the inclusive 8.5 cut.
        experiences.removeIf(e -> e.getGameId() == 3L);
        decimalRun(3, COMPLETADO, "8.49", 1.0, IN_YEAR, null);
        assertThat(compute().surprises()).extracting(RatedGameBriefDto::getGameName)
                .containsExactly("Exactly 8.5");
    }

    @Test
    void topSagasSkipGamesWithoutSagaAndCountDistinctGames() {
        Saga zelda = Saga.builder().id(10L).name("Zelda").build();
        Saga mario = Saga.builder().id(20L).name("Mario").build();
        game(1, "Zelda 1", zelda);
        game(2, "Zelda 2", zelda);
        game(3, "Mario 1", mario);
        game(4, "Standalone");
        run(1, COMPLETADO, 8, 10.0, IN_YEAR, null);
        run(1, COMPLETADO, 9, 5.0, IN_YEAR, null);
        run(2, COMPLETADO, null, 20.0, IN_YEAR, null);
        run(3, COMPLETADO, 7, 3.0, IN_YEAR, null);
        run(4, COMPLETADO, 7, 3.0, IN_YEAR, null);
        run(3, EN_CURSO, 7, 3.0, IN_YEAR, YEAR);

        var sagas = compute().topSagas();

        assertThat(sagas).hasSize(2);
        assertThat(sagas.getFirst().getSagaId()).isEqualTo(10L);
        assertThat(sagas.getFirst().getSagaName()).isEqualTo("Zelda");
        assertThat(sagas.getFirst().getExperienceCount()).isEqualTo(3);
        assertThat(sagas.getFirst().getGameCount()).isEqualTo(2);
        assertThat(sagas.getFirst().getTotalHours()).isEqualTo(35.0);
        assertThat(sagas.get(1).getSagaName()).isEqualTo("Mario");
        assertThat(sagas.get(1).getExperienceCount()).isEqualTo(1);
    }

    @Test
    void anExperienceCountsOnceForEachGenreOfItsGame() {
        Genre rpg = Genre.builder().id(1L).name("RPG").build();
        Genre action = Genre.builder().id(2L).name("Action").build();
        game(1, "Action RPG", null, rpg, action);
        game(2, "Pure RPG", null, rpg);
        run(1, COMPLETADO, 9, 10.0, IN_YEAR, null);
        run(2, COMPLETADO, 8, 4.0, IN_YEAR, null);
        run(2, COMPLETADO, 8, 4.0, IN_YEAR, null);

        var genres = compute().topGenres();

        assertThat(genres).hasSize(2);
        assertThat(genres.getFirst().getGenreName()).isEqualTo("RPG");
        assertThat(genres.getFirst().getExperienceCount()).isEqualTo(3);
        assertThat(genres.getFirst().getGameCount()).isEqualTo(2);
        assertThat(genres.getFirst().getTotalHours()).isEqualTo(18.0);
        assertThat(genres.get(1).getGenreName()).isEqualTo("Action");
        assertThat(genres.get(1).getExperienceCount()).isEqualTo(1);
        assertThat(genres.get(1).getGameCount()).isEqualTo(1);
    }

    private YearHighlights compute() {
        return YearHighlightsCalculator.compute(YEAR, experiences, games);
    }

    private void game(long id, String name) {
        game(id, name, null);
    }

    private void game(long id, String name, Saga saga, Genre... genres) {
        games.put(id, Game.builder().id(id).name(name).saga(saga).genres(Set.of(genres)).build());
    }

    private void run(long gameId, ExperienceStatus status, Integer rating, Double hours, LocalDate endDate, Integer year) {
        decimalRun(gameId, status, rating != null ? rating.toString() : null, hours, endDate, year);
    }

    private void decimalRun(long gameId, ExperienceStatus status, String rating, Double hours, LocalDate endDate, Integer year) {
        experiences.add(Experience.builder()
                .id(nextExperienceId++)
                .gameId(gameId)
                .runLabel("Run")
                .status(status)
                .rating(rating != null ? new BigDecimal(rating) : null)
                .hours(hours)
                .endDate(endDate)
                .year(year)
                .platform(Platform.PC)
                .platinum(false)
                .replay(false)
                .build());
    }
}
