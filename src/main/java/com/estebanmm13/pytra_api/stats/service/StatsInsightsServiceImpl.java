package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryDayTotals;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.OnlineDiaryYearGameHours;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.GameRunTotals;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRow;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GameReleaseRow;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platforms.mapper.PlatformMapper;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.stats.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Loads each breakdown with a few whole-user queries (runs, online playtimes, diary aggregates, projections)
 * and aggregates in memory, so no endpoint issues one query per game, run or platform. Read-only
 * transactions keep each request on a single pooled connection.
 *
 * <p>Counting rules shared with the other stats: run figures cover every run of the user (like the summary);
 * game-grouped figures (genres, developers, release eras) cover CONFIRMED games only (like by-genre and
 * by-saga), since pending imports are not in the library yet.
 */
@Service
public class StatsInsightsServiceImpl implements StatsInsightsService {

    private static final int MAX_LIMIT = 50;
    /** Scale of the intermediate per-game rating means, so group means round only once. */
    private static final int GAME_RATING_SCALE = 10;

    private final DiaryEntryGameRepository diaryEntryGameRepository;
    private final ExperienceRepository experienceRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;
    private final GameRepository gameRepository;
    private final Clock clock;

    @Autowired
    public StatsInsightsServiceImpl(DiaryEntryGameRepository diaryEntryGameRepository,
                                    ExperienceRepository experienceRepository,
                                    OnlinePlaytimeRepository onlinePlaytimeRepository,
                                    GameRepository gameRepository) {
        this(diaryEntryGameRepository, experienceRepository, onlinePlaytimeRepository, gameRepository,
                Clock.systemDefaultZone());
    }

    StatsInsightsServiceImpl(DiaryEntryGameRepository diaryEntryGameRepository,
                             ExperienceRepository experienceRepository,
                             OnlinePlaytimeRepository onlinePlaytimeRepository,
                             GameRepository gameRepository,
                             Clock clock) {
        this.diaryEntryGameRepository = diaryEntryGameRepository;
        this.experienceRepository = experienceRepository;
        this.onlinePlaytimeRepository = onlinePlaytimeRepository;
        this.gameRepository = gameRepository;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- activity

    @Override
    @Transactional(readOnly = true)
    public ActivityStatsDto getActivity(Long userId, Integer year) {
        LocalDate today = LocalDate.now(clock);
        int effectiveYear = year != null ? year : today.getYear();
        StatsServiceImpl.requireValidYear(effectiveYear);

        List<DiaryDayTotals> rows = diaryEntryGameRepository.sumHoursByDay(
                userId, LocalDate.of(effectiveYear, 1, 1), LocalDate.of(effectiveYear, 12, 31));

        BigDecimal totalHours = BigDecimal.ZERO;
        long[] daysByWeekday = new long[7];
        BigDecimal[] hoursByWeekday = new BigDecimal[7];
        Arrays.fill(hoursByWeekday, BigDecimal.ZERO);
        List<ActivityStatsDto.Day> days = new ArrayList<>(rows.size());
        for (DiaryDayTotals row : rows) {
            BigDecimal hours = row.hours() != null ? row.hours() : BigDecimal.ZERO;
            totalHours = totalHours.add(hours);
            int weekday = row.date().getDayOfWeek().getValue() - 1;
            daysByWeekday[weekday]++;
            hoursByWeekday[weekday] = hoursByWeekday[weekday].add(hours);
            days.add(new ActivityStatsDto.Day(row.date(), hours.doubleValue(),
                    row.gameCount() != null ? row.gameCount() : 0L));
        }

        List<ActivityStatsDto.Weekday> weekdays = new ArrayList<>(7);
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            int index = dayOfWeek.getValue() - 1;
            weekdays.add(new ActivityStatsDto.Weekday(dayOfWeek.getValue(), dayOfWeek.name(),
                    daysByWeekday[index], hoursByWeekday[index].doubleValue()));
        }

        List<LocalDate> playedDates = diaryEntryGameRepository.findPlayedDates(userId);
        return new ActivityStatsDto(
                effectiveYear,
                totalHours.doubleValue(),
                days.size(),
                days,
                ActivityStreaks.current(playedDates, today),
                ActivityStreaks.longest(playedDates),
                weekdays);
    }

    // ---------------------------------------------------------------- platforms

    @Override
    @Transactional(readOnly = true)
    public PlatformStatsDto getPlatforms(Long userId, Integer year) {
        requireValidYearIfPresent(year);
        List<Experience> runs = experienceRepository.findAllByUserId(userId);
        List<OnlinePlaytime> onlinePlaytimes = onlinePlaytimeRepository.findAllByUserId(userId);
        List<OnlineDiaryYearGameHours> onlineDiary = diaryEntryGameRepository.sumCountedOnlineHoursByYearAndGame(userId);

        Map<Long, Platform> catalog = new HashMap<>();
        Map<Long, List<Platform>> onlinePlatformsByGame = new HashMap<>();
        for (OnlinePlaytime playtime : onlinePlaytimes) {
            List<Platform> platforms = new ArrayList<>(playtime.getPlatforms());
            platforms.sort(PlatformMapper.CATALOG_ORDER);
            platforms.forEach(platform -> catalog.put(platform.getId(), platform));
            onlinePlatformsByGame.put(playtime.getGameId(), platforms);
        }

        // Platforms in scope.
        Map<Long, PlatformAccumulator> byPlatform = new HashMap<>();
        double unassigned = 0;
        for (Experience run : runs) {
            if (year != null && !year.equals(ExperiencePeriod.yearOf(run))) {
                continue;
            }
            Platform platform = run.getPlatform();
            catalog.put(platform.getId(), platform);
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
                        onlinePlatformsByGame, byPlatform);
            }
        } else {
            for (OnlineDiaryYearGameHours row : onlineDiary) {
                if (year.equals(row.year())) {
                    unassigned += splitOnline(row.gameId(), doubleOf(row.hours()), onlinePlatformsByGame, byPlatform);
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
                platformHoursByYear(runs, onlineDiary, onlinePlatformsByGame, catalog));
    }

    /** Every year's hours per platform: runs by their year, online by the diary year (same split rule). */
    private static List<PlatformStatsDto.YearPlatforms> platformHoursByYear(
            List<Experience> runs, List<OnlineDiaryYearGameHours> onlineDiary,
            Map<Long, List<Platform>> onlinePlatformsByGame, Map<Long, Platform> catalog) {
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
            double unassigned = splitOnline(row.gameId(), doubleOf(row.hours()), onlinePlatformsByGame,
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
     * Splits a game's online hours evenly across the platforms of its online playtime (the documented rule).
     * Returns the hours that have no platform to go to.
     */
    private static double splitOnline(Long gameId, double hours, Map<Long, List<Platform>> onlinePlatformsByGame,
                                      Map<Long, PlatformAccumulator> byPlatform) {
        if (hours <= 0) {
            return 0;
        }
        List<Platform> platforms = onlinePlatformsByGame.getOrDefault(gameId, List.of());
        if (platforms.isEmpty()) {
            return hours;
        }
        double share = hours / platforms.size();
        for (Platform platform : platforms) {
            PlatformAccumulator acc = byPlatform.computeIfAbsent(platform.getId(), id -> new PlatformAccumulator());
            acc.onlineHours += share;
            acc.gameIds.add(gameId);
        }
        return 0;
    }

    private static final class PlatformAccumulator {
        double runHours;
        double onlineHours;
        final Set<Long> gameIds = new HashSet<>();
        long runCount;
        long completedRunCount;
        final List<BigDecimal> ratings = new ArrayList<>();
    }

    // ---------------------------------------------------------------- developers & release eras

    @Override
    @Transactional(readOnly = true)
    public DeveloperStatsDto getDevelopers(Long userId, int limit, int minRatedGames) {
        int max = clampLimit(limit);
        int threshold = Math.max(minRatedGames, 1);

        // Grouping key: trimmed, lower-cased name; the label is the first spelling met (games in id order).
        Map<String, GroupAccumulator> byDeveloper = new LinkedHashMap<>();
        for (GameFacts game : confirmedGameFacts(userId)) {
            String developer = game.developer() != null ? game.developer().trim() : "";
            if (developer.isEmpty()) {
                continue;
            }
            byDeveloper.computeIfAbsent(developer.toLowerCase(Locale.ROOT), key -> new GroupAccumulator(developer))
                    .add(game);
        }

        List<DeveloperStatsDto.DeveloperStat> developers = new ArrayList<>();
        for (GroupAccumulator acc : byDeveloper.values()) {
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

    @Override
    @Transactional(readOnly = true)
    public ReleaseEraStatsDto getReleaseEras(Long userId) {
        SortedMap<Integer, GroupAccumulator> byDecade = new TreeMap<>();
        SortedMap<Integer, GroupAccumulator> byYear = new TreeMap<>();
        long undated = 0;
        for (GameFacts game : confirmedGameFacts(userId)) {
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

    /** A CONFIRMED game with its total hours (runs + online) and its rating (null when unrated). */
    private record GameFacts(Long gameId, String developer, LocalDate releaseDate, double hours, BigDecimal rating) {
    }

    /**
     * Three whole-user queries: game projections, run totals per game and online playtime rows. A game's
     * rating is the mean of its rated runs and its online general rating, each counting once (like the summary).
     */
    private List<GameFacts> confirmedGameFacts(Long userId) {
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
            facts.add(new GameFacts(game.gameId(), game.developer(), game.releaseDate(), hours, rating));
        }
        return facts;
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

    // ---------------------------------------------------------------- helpers

    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private static void requireValidYearIfPresent(Integer year) {
        if (year != null) {
            StatsServiceImpl.requireValidYear(year);
        }
    }

    private static double hoursOf(Experience experience) {
        return experience.getHours() != null ? experience.getHours() : 0.0;
    }

    private static double doubleOf(Number value) {
        return value != null ? value.doubleValue() : 0.0;
    }
}
