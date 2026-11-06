package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.OnlineDiaryYearGameHours;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.platforms.mapper.PlatformMapper;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.stats.dto.PlatformStatsDto;

import java.math.BigDecimal;
import java.util.*;

import static com.estebanmm13.pytra_api.stats.service.InsightMath.doubleOf;
import static com.estebanmm13.pytra_api.stats.service.InsightMath.hoursOf;
import static com.estebanmm13.pytra_api.stats.service.InsightMath.requireValidYearIfPresent;

/** Hours and run figures per platform: the selected scope plus the every-year breakdown. */
final class PlatformInsights {

    private final DiaryEntryGameRepository diaryEntryGameRepository;
    private final ExperienceRepository experienceRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;

    PlatformInsights(DiaryEntryGameRepository diaryEntryGameRepository,
                     ExperienceRepository experienceRepository,
                     OnlinePlaytimeRepository onlinePlaytimeRepository) {
        this.diaryEntryGameRepository = diaryEntryGameRepository;
        this.experienceRepository = experienceRepository;
        this.onlinePlaytimeRepository = onlinePlaytimeRepository;
    }

    PlatformStatsDto platforms(Long userId, Integer year) {
        requireValidYearIfPresent(year);
        List<Experience> runs = experienceRepository.findAllByUserId(userId);
        List<OnlinePlaytime> onlinePlaytimes = onlinePlaytimeRepository.findAllByUserId(userId);
        List<OnlineDiaryYearGameHours> onlineDiary = diaryEntryGameRepository.sumCountedOnlineHoursByYearAndGame(userId);

        Map<Long, Platform> catalog = new HashMap<>();
        Map<Long, OnlineSplit> onlineSplitByGame = new HashMap<>();
        for (OnlinePlaytime playtime : onlinePlaytimes) {
            OnlineSplit split = OnlineSplit.of(playtime);
            split.platforms().forEach(platform -> catalog.put(platform.getId(), platform));
            onlineSplitByGame.put(playtime.getGameId(), split);
        }

        // Platforms in scope.
        Map<Long, PlatformAccumulator> byPlatform = new HashMap<>();
        double unassigned = 0;
        for (Experience run : runs) {
            // Catalog every run's platform before the year filter: the per-year breakdown covers all years.
            Platform platform = run.getPlatform();
            catalog.put(platform.getId(), platform);
            if (year != null && !year.equals(ExperiencePeriod.yearOf(run))) {
                continue;
            }
            PlatformAccumulator acc = byPlatform.computeIfAbsent(platform.getId(), id -> new PlatformAccumulator());
            acc.runHours += hoursOf(run);
            acc.gameIds.add(run.getGameId());
            acc.runCount++;
            if (run.getStatus() == ExperienceStatus.COMPLETADO) acc.completedRunCount++;
            acc.ratings.add(run.getRating());
        }
        if (year == null) {
            for (OnlinePlaytime playtime : onlinePlaytimes) {
                unassigned += splitOnline(playtime.getGameId(), doubleOf(playtime.getTotalHours()),
                        onlineSplitByGame, byPlatform);
            }
        } else {
            for (OnlineDiaryYearGameHours row : onlineDiary) {
                if (year.equals(row.year())) {
                    unassigned += splitOnline(row.gameId(), doubleOf(row.hours()), onlineSplitByGame, byPlatform);
                }
            }
        }

        List<PlatformStatsDto.PlatformStat> platforms = new ArrayList<>();
        for (Map.Entry<Long, PlatformAccumulator> entry : byPlatform.entrySet()) {
            Platform platform = catalog.get(entry.getKey());
            PlatformAccumulator acc = entry.getValue();
            platforms.add(new PlatformStatsDto.PlatformStat(
                    platform.getId(),
                    platform.getName(),
                    PlatformMapper.colorOf(platform),
                    platform.getFamily() != null ? platform.getFamily().name() : null,
                    StatsMath.round2(acc.runHours + acc.onlineHours),
                    StatsMath.round2(acc.runHours),
                    StatsMath.round2(acc.onlineHours),
                    acc.gameIds.size(),
                    acc.runCount,
                    acc.completedRunCount,
                    acc.ratings.stream().filter(Objects::nonNull).count(),
                    StatsMath.averageRating(acc.ratings)));
        }
        platforms.sort(Comparator
                .comparingDouble(PlatformStatsDto.PlatformStat::hours).reversed()
                .thenComparing(stat -> catalog.get(stat.platformId()), PlatformMapper.CATALOG_ORDER));

        return new PlatformStatsDto(year, platforms, StatsMath.round2(unassigned),
                platformHoursByYear(runs, onlineDiary, onlineSplitByGame, catalog));
    }

    /** Every year's hours per platform: runs by their year, online by the diary year (same split rule). */
    private static List<PlatformStatsDto.YearPlatforms> platformHoursByYear(
            List<Experience> runs, List<OnlineDiaryYearGameHours> onlineDiary,
            Map<Long, OnlineSplit> onlineSplitByGame, Map<Long, Platform> catalog) {
        SortedMap<Integer, Map<Long, PlatformAccumulator>> byYear = new TreeMap<>();
        Map<Integer, Double> unassignedByYear = new HashMap<>();
        for (Experience run : runs) {
            Integer year = ExperiencePeriod.yearOf(run);
            double hours = hoursOf(run);
            if (year == null || hours <= 0) {
                continue;
            }
            byYear.computeIfAbsent(year, y -> new HashMap<>())
                    .computeIfAbsent(run.getPlatform().getId(), id -> new PlatformAccumulator())
                    .runHours += hours;
        }
        for (OnlineDiaryYearGameHours row : onlineDiary) {
            if (row.year() == null) {
                continue;
            }
            double unassigned = splitOnline(row.gameId(), doubleOf(row.hours()), onlineSplitByGame,
                    byYear.computeIfAbsent(row.year(), y -> new HashMap<>()));
            if (unassigned > 0) {
                unassignedByYear.merge(row.year(), unassigned, Double::sum);
            }
        }

        List<PlatformStatsDto.YearPlatforms> result = new ArrayList<>();
        for (Map.Entry<Integer, Map<Long, PlatformAccumulator>> entry : byYear.entrySet()) {
            double unassigned = unassignedByYear.getOrDefault(entry.getKey(), 0.0);
            double total = unassigned;
            List<PlatformStatsDto.PlatformHours> platforms = new ArrayList<>();
            for (Map.Entry<Long, PlatformAccumulator> platformEntry : entry.getValue().entrySet()) {
                Platform platform = catalog.get(platformEntry.getKey());
                double hours = platformEntry.getValue().runHours + platformEntry.getValue().onlineHours;
                total += hours;
                platforms.add(new PlatformStatsDto.PlatformHours(platform.getId(), platform.getName(),
                        PlatformMapper.colorOf(platform), StatsMath.round2(hours)));
            }
            if (platforms.isEmpty() && unassigned <= 0) {
                continue;
            }
            platforms.sort(Comparator
                    .comparingDouble(PlatformStatsDto.PlatformHours::hours).reversed()
                    .thenComparing(hours -> catalog.get(hours.platformId()), PlatformMapper.CATALOG_ORDER));
            result.add(new PlatformStatsDto.YearPlatforms(entry.getKey(), StatsMath.round2(total),
                    StatsMath.round2(unassigned), platforms));
        }
        return result;
    }

    /**
     * Splits a game's online hours across the platforms of its online playtime with its {@link OnlineSplit}.
     * Returns the hours that have no platform to go to.
     */
    private static double splitOnline(Long gameId, double hours, Map<Long, OnlineSplit> onlineSplitByGame,
                                      Map<Long, PlatformAccumulator> byPlatform) {
        if (hours <= 0) {
            return 0;
        }
        OnlineSplit split = onlineSplitByGame.get(gameId);
        if (split == null || split.platforms().isEmpty()) {
            return hours;
        }
        for (Platform platform : split.platforms()) {
            PlatformAccumulator acc = byPlatform.computeIfAbsent(platform.getId(), id -> new PlatformAccumulator());
            acc.onlineHours += hours * split.shares().get(platform.getId());
            acc.gameIds.add(gameId);
        }
        return hours * split.unassignedShare();
    }

    /**
     * How a game's online hours (all-time or one year's) go to its online platforms, as shares of 1.
     * With the user's per-platform breakdown each platform gets its assigned part of the total and the
     * rest is unassigned; a breakdown above a total lowered later is scaled down to it. Without a
     * breakdown the hours are split evenly (the documented default).
     */
    private record OnlineSplit(List<Platform> platforms, Map<Long, Double> shares, double unassignedShare) {

        static OnlineSplit of(OnlinePlaytime playtime) {
            List<Platform> platforms = new ArrayList<>(playtime.getPlatforms());
            platforms.sort(PlatformMapper.CATALOG_ORDER);
            Map<Long, Double> shares = new HashMap<>();
            double assigned = platforms.stream()
                    .mapToDouble(platform -> playtime.getPlatformHours().getOrDefault(platform.getId(), 0.0))
                    .sum();
            double base = Math.max(doubleOf(playtime.getTotalHours()), assigned);
            if (platforms.size() < 2 || assigned <= 0) {
                platforms.forEach(platform -> shares.put(platform.getId(), 1.0 / platforms.size()));
                return new OnlineSplit(platforms, shares, 0);
            }
            platforms.forEach(platform -> shares.put(platform.getId(),
                    playtime.getPlatformHours().getOrDefault(platform.getId(), 0.0) / base));
            return new OnlineSplit(platforms, shares, 1 - assigned / base);
        }
    }

    private static final class PlatformAccumulator {
        double runHours;
        double onlineHours;
        final Set<Long> gameIds = new HashSet<>();
        long runCount;
        long completedRunCount;
        final List<BigDecimal> ratings = new ArrayList<>();
    }
}
