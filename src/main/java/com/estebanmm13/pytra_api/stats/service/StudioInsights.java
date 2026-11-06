package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.stats.dto.DeveloperStatsDto;
import com.estebanmm13.pytra_api.stats.dto.ReleaseEraStatsDto;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

import static com.estebanmm13.pytra_api.stats.service.InsightMath.clampLimit;

/** Library grouped by developer, publisher and release era. */
final class StudioInsights {

    private final InsightSources sources;

    StudioInsights(InsightSources sources) {
        this.sources = sources;
    }

    DeveloperStatsDto developers(Long userId, int limit, int minRatedGames) {
        return studios(userId, limit, minRatedGames, GameFacts::developer);
    }

    DeveloperStatsDto publishers(Long userId, int limit, int minRatedGames) {
        return studios(userId, limit, minRatedGames, GameFacts::publisher);
    }

    /** Studios (developers or publishers, by {@code studioOf}) ranked by hours and by rating. */
    private DeveloperStatsDto studios(Long userId, int limit, int minRatedGames, Function<GameFacts, String> studioOf) {
        // 0 (or less) lists every studio: the studios page shows them all.
        int max = limit <= 0 ? Integer.MAX_VALUE : clampLimit(limit);
        int threshold = Math.max(minRatedGames, 1);

        // Grouping key: trimmed, lower-cased name; the label is the first spelling met (games in id order).
        Map<String, GroupAccumulator> byStudio = new LinkedHashMap<>();
        for (GameFacts game : sources.confirmedGameFacts(userId)) {
            String name = studioOf.apply(game);
            String studio = name != null ? name.trim() : "";
            if (studio.isEmpty()) {
                continue;
            }
            byStudio.computeIfAbsent(studio.toLowerCase(Locale.ROOT), key -> new GroupAccumulator(studio)).add(game);
        }

        List<DeveloperStatsDto.DeveloperStat> developers = new ArrayList<>();
        for (GroupAccumulator acc : byStudio.values()) {
            developers.add(new DeveloperStatsDto.DeveloperStat(acc.label, acc.gameCount,
                    StatsMath.round2(acc.hours), acc.ratings.size(), StatsMath.averageRating(acc.ratings)));
        }
        Comparator<DeveloperStatsDto.DeveloperStat> byName =
                Comparator.comparing(DeveloperStatsDto.DeveloperStat::developer, String.CASE_INSENSITIVE_ORDER);

        List<DeveloperStatsDto.DeveloperStat> topByHours = developers.stream()
                .sorted(Comparator.comparingDouble(DeveloperStatsDto.DeveloperStat::hours).reversed()
                        .thenComparing(byName))
                .limit(max)
                .toList();
        List<DeveloperStatsDto.DeveloperStat> topByRating = developers.stream()
                .filter(developer -> developer.ratedGameCount() >= threshold)
                .sorted(Comparator.comparing(DeveloperStatsDto.DeveloperStat::averageRating, Comparator.reverseOrder())
                        .thenComparing(Comparator.comparingLong(DeveloperStatsDto.DeveloperStat::ratedGameCount).reversed())
                        .thenComparing(byName))
                .limit(max)
                .toList();
        return new DeveloperStatsDto(threshold, topByHours, topByRating);
    }

    ReleaseEraStatsDto releaseEras(Long userId) {
        SortedMap<Integer, GroupAccumulator> byDecade = new TreeMap<>();
        SortedMap<Integer, GroupAccumulator> byYear = new TreeMap<>();
        long undated = 0;
        for (GameFacts game : sources.confirmedGameFacts(userId)) {
            if (game.releaseDate() == null) {
                undated++;
                continue;
            }
            int year = game.releaseDate().getYear();
            byYear.computeIfAbsent(year, y -> new GroupAccumulator(null)).add(game);
            byDecade.computeIfAbsent(Math.floorDiv(year, 10) * 10, d -> new GroupAccumulator(null)).add(game);
        }

        List<ReleaseEraStatsDto.DecadeStat> decades = new ArrayList<>();
        byDecade.forEach((decade, acc) -> decades.add(new ReleaseEraStatsDto.DecadeStat(decade, acc.gameCount,
                StatsMath.round2(acc.hours), acc.ratings.size(), StatsMath.averageRating(acc.ratings))));
        List<ReleaseEraStatsDto.YearStat> years = new ArrayList<>();
        byYear.forEach((year, acc) -> years.add(new ReleaseEraStatsDto.YearStat(year, acc.gameCount,
                StatsMath.round2(acc.hours), acc.ratings.size(), StatsMath.averageRating(acc.ratings))));
        return new ReleaseEraStatsDto(decades, years, undated);
    }

    /** Games of one group: how many, their hours and their (non-null) ratings. */
    private static final class GroupAccumulator {
        final String label;
        long gameCount;
        double hours;
        final List<BigDecimal> ratings = new ArrayList<>();

        GroupAccumulator(String label) {
            this.label = label;
        }

        void add(GameFacts game) {
            gameCount++;
            hours += game.hours();
            if (game.rating() != null) {
                ratings.add(game.rating());
            }
        }
    }
}
