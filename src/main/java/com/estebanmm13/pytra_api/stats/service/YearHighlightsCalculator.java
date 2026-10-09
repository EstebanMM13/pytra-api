package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.stats.dto.MostPlayedGameDto;
import com.estebanmm13.pytra_api.stats.dto.RatedGameBriefDto;
import com.estebanmm13.pytra_api.stats.dto.RatedGameDto;
import com.estebanmm13.pytra_api.stats.dto.YearGenreStatDto;
import com.estebanmm13.pytra_api.stats.dto.YearSagaStatDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Automatic yearly highlights, replicating the user's Obsidian rules. Pure: works on already loaded
 * experiences and games (saga and genres initialized), so it is unit-testable without a database.
 *
 * <p>Two year attributions, deliberately different from {@code ExperiencePeriod} (which drives hours,
 * months and counts of the year summary):
 * <ul>
 *   <li><b>Completed in year</b>: status COMPLETADO and {@code endDate} in the year. Feeds goty,
 *       topRated, topSagas, topGenres, surprises and disappointments.</li>
 *   <li><b>Played in year</b>: the {@code year} field equals the year (any status) and hours are
 *       not null. Feeds mostPlayed only.</li>
 * </ul>
 * Averages are rounded to 2 decimals before ranking and before the inclusive thresholds
 * (surprise >= 8.5, disappointment <= 6). Ties: more hours first, then game name.
 */
public final class YearHighlightsCalculator {

    public static final int TOP_LIMIT = 5;
    public static final int SURPRISE_LIMIT = 3;
    public static final int DISAPPOINTMENT_LIMIT = 3;
    public static final double SURPRISE_MIN_RATING = 8.5;
    public static final double DISAPPOINTMENT_MAX_RATING = 6.0;

    private static final Comparator<String> BY_NAME =
            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<RatedGameDto> BEST_FIRST = Comparator
            .comparing(RatedGameDto::getAvgRating, Comparator.reverseOrder())
            .thenComparing(RatedGameDto::getTotalHours, Comparator.reverseOrder())
            .thenComparing(RatedGameDto::getGameName, BY_NAME)
            .thenComparing(RatedGameDto::getGameId);

    private static final Comparator<RatedGameDto> WORST_FIRST = Comparator
            .comparing(RatedGameDto::getAvgRating)
            .thenComparing(RatedGameDto::getTotalHours, Comparator.reverseOrder())
            .thenComparing(RatedGameDto::getGameName, BY_NAME)
            .thenComparing(RatedGameDto::getGameId);

    private YearHighlightsCalculator() {
    }

    public record YearHighlights(
            RatedGameDto goty,
            List<RatedGameDto> topRated,
            List<MostPlayedGameDto> mostPlayed,
            List<YearSagaStatDto> topSagas,
            List<YearGenreStatDto> topGenres,
            List<RatedGameBriefDto> surprises,
            List<RatedGameBriefDto> disappointments) {
    }

    public static boolean isCompletedIn(Experience experience, int year) {
        return experience.getStatus() == ExperienceStatus.COMPLETADO
                && experience.getEndDate() != null
                && experience.getEndDate().getYear() == year;
    }

    public static boolean isPlayedIn(Experience experience, int year) {
        return experience.getYear() != null
                && experience.getYear() == year
                && experience.getHours() != null;
    }

    /**
     * @param experiences all the user's experiences (filtered here)
     * @param gamesById   the user's games with saga and genres loaded
     */
    public static YearHighlights compute(int year, Collection<Experience> experiences, Map<Long, Game> gamesById) {
        List<Experience> completed = new ArrayList<>();
        List<Experience> played = new ArrayList<>();
        for (Experience experience : experiences) {
            if (!gamesById.containsKey(experience.getGameId())) {
                continue;
            }
            if (isCompletedIn(experience, year)) completed.add(experience);
            if (isPlayedIn(experience, year)) played.add(experience);
        }

        List<RatedGameDto> ranked = rankByRating(completed, gamesById);
        RatedGameDto goty = ranked.isEmpty() ? null : ranked.getFirst();

        List<RatedGameBriefDto> surprises = ranked.stream()
                .filter(game -> game.getAvgRating() >= SURPRISE_MIN_RATING)
                .filter(game -> goty == null || !game.getGameId().equals(goty.getGameId()))
                .limit(SURPRISE_LIMIT)
                .map(YearHighlightsCalculator::brief)
                .toList();

        List<RatedGameBriefDto> disappointments = ranked.stream()
                .filter(game -> game.getAvgRating() <= DISAPPOINTMENT_MAX_RATING)
                .sorted(WORST_FIRST)
                .limit(DISAPPOINTMENT_LIMIT)
                .map(YearHighlightsCalculator::brief)
                .toList();

        return new YearHighlights(
                goty,
                ranked.stream().limit(TOP_LIMIT).toList(),
                mostPlayed(played, gamesById),
                topSagas(completed, gamesById),
                topGenres(completed, gamesById),
                surprises,
                disappointments);
    }

    /** Games with at least one rated "completed in year" run, best average first. */
    private static List<RatedGameDto> rankByRating(List<Experience> completed, Map<Long, Game> gamesById) {
        Map<Long, List<Experience>> byGame = groupBy(completed, Experience::getGameId);
        List<RatedGameDto> ranked = new ArrayList<>();
        byGame.forEach((gameId, runs) -> {
            Double avg = StatsMath.averageRating(runs.stream().map(Experience::getRating).toList());
            if (avg != null) {
                ranked.add(new RatedGameDto(gameId, gamesById.get(gameId).getName(), avg, runs.size(), sumHours(runs)));
            }
        });
        ranked.sort(BEST_FIRST);
        return ranked;
    }

    private static List<MostPlayedGameDto> mostPlayed(List<Experience> played, Map<Long, Game> gamesById) {
        List<MostPlayedGameDto> result = new ArrayList<>();
        groupBy(played, Experience::getGameId).forEach((gameId, runs) ->
                result.add(new MostPlayedGameDto(gameId, gamesById.get(gameId).getName(), sumHours(runs))));
        result.sort(Comparator
                .comparing(MostPlayedGameDto::getTotalHours, Comparator.reverseOrder())
                .thenComparing(MostPlayedGameDto::getGameName, BY_NAME)
                .thenComparing(MostPlayedGameDto::getGameId));
        return result.stream().limit(TOP_LIMIT).toList();
    }

    private static List<YearSagaStatDto> topSagas(List<Experience> completed, Map<Long, Game> gamesById) {
        Map<Long, Bucket> buckets = new LinkedHashMap<>();
        for (Experience experience : completed) {
            Game game = gamesById.get(experience.getGameId());
            if (game.getSaga() != null) {
                buckets.computeIfAbsent(game.getSaga().getId(), id -> new Bucket(id, game.getSaga().getName()))
                        .add(experience);
            }
        }
        return sortedBuckets(buckets.values()).stream()
                .map(b -> new YearSagaStatDto(b.id, b.name, b.experienceCount, b.gameIds.size(), b.totalHours))
                .toList();
    }

    /** Each run counts once for every genre of its game. */
    private static List<YearGenreStatDto> topGenres(List<Experience> completed, Map<Long, Game> gamesById) {
        Map<Long, Bucket> buckets = new LinkedHashMap<>();
        for (Experience experience : completed) {
            for (Genre genre : gamesById.get(experience.getGameId()).getGenres()) {
                buckets.computeIfAbsent(genre.getId(), id -> new Bucket(id, genre.getName())).add(experience);
            }
        }
        return sortedBuckets(buckets.values()).stream()
                .map(b -> new YearGenreStatDto(b.id, b.name, b.experienceCount, b.gameIds.size(), b.totalHours))
                .toList();
    }

    private static List<Bucket> sortedBuckets(Collection<Bucket> buckets) {
        return buckets.stream()
                .sorted(Comparator
                        .comparingLong((Bucket b) -> b.experienceCount).reversed()
                        .thenComparing(b -> b.totalHours, Comparator.reverseOrder())
                        .thenComparing(b -> b.name, BY_NAME)
                        .thenComparing(b -> b.id))
                .limit(TOP_LIMIT)
                .toList();
    }

    private static RatedGameBriefDto brief(RatedGameDto game) {
        return new RatedGameBriefDto(game.getGameId(), game.getGameName(), game.getAvgRating());
    }

    private static double sumHours(List<Experience> runs) {
        double total = 0;
        for (Experience run : runs) {
            total += run.getHours() != null ? run.getHours() : 0.0;
        }
        return total;
    }

    private static <K> Map<K, List<Experience>> groupBy(List<Experience> experiences, Function<Experience, K> key) {
        Map<K, List<Experience>> grouped = new LinkedHashMap<>();
        for (Experience experience : experiences) {
            grouped.computeIfAbsent(key.apply(experience), k -> new ArrayList<>()).add(experience);
        }
        return grouped;
    }

    private static final class Bucket {
        final Long id;
        final String name;
        final Set<Long> gameIds = new HashSet<>();
        long experienceCount;
        double totalHours;

        Bucket(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        void add(Experience experience) {
            experienceCount++;
            gameIds.add(experience.getGameId());
            totalHours += experience.getHours() != null ? experience.getHours() : 0.0;
        }
    }
}
