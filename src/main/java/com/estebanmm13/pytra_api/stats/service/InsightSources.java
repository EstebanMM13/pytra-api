package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.GameRunTotals;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRow;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GameGenreRow;
import com.estebanmm13.pytra_api.games.repository.GameReleaseRow;
import com.estebanmm13.pytra_api.games.repository.GameRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.estebanmm13.pytra_api.stats.service.InsightMath.doubleOf;

/** Whole-user queries that more than one stats insight reads. */
final class InsightSources {

    /** Scale of the intermediate per-game rating means, so group means round only once. */
    private static final int GAME_RATING_SCALE = 10;

    private final ExperienceRepository experienceRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;
    private final GameRepository gameRepository;

    InsightSources(ExperienceRepository experienceRepository,
                   OnlinePlaytimeRepository onlinePlaytimeRepository,
                   GameRepository gameRepository) {
        this.experienceRepository = experienceRepository;
        this.onlinePlaytimeRepository = onlinePlaytimeRepository;
        this.gameRepository = gameRepository;
    }

    /**
     * Three whole-user queries: game projections, run totals per game and online playtime rows. A game's
     * rating is the mean of its rated runs and its online general rating, each counting once (like the summary).
     */
    List<GameFacts> confirmedGameFacts(Long userId) {
        Map<Long, GameRunTotals> runsByGame = new HashMap<>();
        for (GameRunTotals totals : experienceRepository.totalsGroupedByGameId(userId)) {
            runsByGame.put(totals.gameId(), totals);
        }
        Map<Long, OnlinePlaytimeRow> onlineByGame = new HashMap<>();
        for (OnlinePlaytimeRow row : onlinePlaytimeRepository.findRowsByUserId(userId)) {
            onlineByGame.put(row.gameId(), row);
        }

        List<GameFacts> facts = new ArrayList<>();
        for (GameReleaseRow game : gameRepository.findReleaseRowsByUserIdAndReviewStatus(userId, ReviewStatus.CONFIRMED)) {
            GameRunTotals runs = runsByGame.get(game.gameId());
            OnlinePlaytimeRow online = onlineByGame.get(game.gameId());
            double hours = (runs != null ? doubleOf(runs.hours()) : 0)
                    + (online != null ? doubleOf(online.totalHours()) : 0);
            BigDecimal ratingSum = runs != null ? runs.ratingSum() : null;
            long ratedCount = runs != null && runs.ratedCount() != null ? runs.ratedCount() : 0;
            if (online != null && online.generalRating() != null) {
                ratingSum = StatsMath.sum(ratingSum, online.generalRating());
                ratedCount++;
            }
            BigDecimal rating = ratedCount == 0 || ratingSum == null ? null
                    : ratingSum.divide(BigDecimal.valueOf(ratedCount), GAME_RATING_SCALE, RoundingMode.HALF_UP);
            facts.add(new GameFacts(game.gameId(), game.developer(), game.publisher(), game.releaseDate(), hours, rating));
        }
        return facts;
    }

    /** Every run of the user, or only those of {@code year} (ExperiencePeriod year rule). */
    List<Experience> runsInScope(Long userId, Integer year) {
        List<Experience> runs = experienceRepository.findAllByUserId(userId);
        if (year == null) {
            return runs;
        }
        return runs.stream().filter(run -> year.equals(ExperiencePeriod.yearOf(run))).toList();
    }

    /** Genres of the CONFIRMED games, per game id (games without genres are absent). */
    Map<Long, List<GameGenreRow>> confirmedGenresByGame(Long userId) {
        Map<Long, List<GameGenreRow>> byGame = new HashMap<>();
        for (GameGenreRow row : gameRepository.findGenreRowsByUserIdAndReviewStatus(userId, ReviewStatus.CONFIRMED)) {
            byGame.computeIfAbsent(row.gameId(), id -> new ArrayList<>()).add(row);
        }
        return byGame;
    }
}
