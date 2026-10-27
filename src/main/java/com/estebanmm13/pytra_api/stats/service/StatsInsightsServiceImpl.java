package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryDayTotals;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.OnlineDiaryYearGameHours;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.model.Ratings;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.GameRunTotals;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRow;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GameGenreRow;
import com.estebanmm13.pytra_api.games.repository.GameLabel;
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
import java.time.temporal.ChronoUnit;
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

    // ---------------------------------------------------------------- completion habits

    private record DayRange(String label, int min, Integer max) {
        boolean contains(long days) {
            return days >= min && (max == null || days <= max);
        }
    }

    private record HourRange(String label, double min, Double max) {
        boolean contains(double hours) {
            return hours >= min && (max == null || hours < max);
        }
    }

    private static final List<DayRange> DAY_BUCKETS = List.of(
            new DayRange("0-7", 0, 7),
            new DayRange("8-30", 8, 30),
            new DayRange("31-90", 31, 90),
            new DayRange("91-180", 91, 180),
            new DayRange("181-365", 181, 365),
            new DayRange("366+", 366, null));

    private static final List<HourRange> HOUR_BUCKETS = List.of(
            new HourRange("<10", 0, 10.0),
            new HourRange("10-30", 10, 30.0),
            new HourRange("30-60", 30, 60.0),
            new HourRange("60+", 60, null));

    private record TimedRun(Experience run, long days) {
    }

    @Override
    @Transactional(readOnly = true)
    public CompletionStatsDto getCompletion(Long userId, Integer year) {
        requireValidYearIfPresent(year);
        List<Experience> runs = runsInScope(userId, year);
        Map<Long, List<GameGenreRow>> genresByGame = confirmedGenresByGame(userId);

        long completed = 0;
        long abandoned = 0;
        long replays = 0;
        List<TimedRun> timed = new ArrayList<>();
        Map<Long, long[]> outcomesByGenre = new LinkedHashMap<>(); // [completed, abandoned]
        Map<Long, String> genreNames = new HashMap<>();
        List<List<BigDecimal>> ratingsByHours = new ArrayList<>();
        HOUR_BUCKETS.forEach(bucket -> ratingsByHours.add(new ArrayList<>()));

        for (Experience run : runs) {
            if (Boolean.TRUE.equals(run.getReplay())) replays++;
            boolean isCompleted = run.getStatus() == ExperienceStatus.COMPLETADO;
            boolean isAbandoned = run.getStatus() == ExperienceStatus.ABANDONADO;
            if (isCompleted) {
                completed++;
                if (run.getStartDate() != null && run.getEndDate() != null
                        && !run.getEndDate().isBefore(run.getStartDate())) {
                    timed.add(new TimedRun(run, ChronoUnit.DAYS.between(run.getStartDate(), run.getEndDate())));
                }
                for (int i = 0; i < HOUR_BUCKETS.size(); i++) {
                    if (HOUR_BUCKETS.get(i).contains(hoursOf(run))) {
                        ratingsByHours.get(i).add(run.getRating());
                        break;
                    }
                }
            }
            if (isAbandoned) abandoned++;
            if (isCompleted || isAbandoned) {
                for (GameGenreRow genre : genresByGame.getOrDefault(run.getGameId(), List.of())) {
                    genreNames.put(genre.genreId(), genre.genreName());
                    outcomesByGenre.computeIfAbsent(genre.genreId(), id -> new long[2])[isCompleted ? 0 : 1]++;
                }
            }
        }

        List<CompletionStatsDto.GenreAbandonment> abandonment = new ArrayList<>();
        outcomesByGenre.forEach((genreId, counts) -> abandonment.add(new CompletionStatsDto.GenreAbandonment(
                genreId, genreNames.get(genreId), counts[0], counts[1],
                BigDecimal.valueOf(counts[1])
                        .divide(BigDecimal.valueOf(counts[0] + counts[1]), 4, RoundingMode.HALF_UP)
                        .doubleValue())));
        abandonment.sort(Comparator
                .comparingDouble(CompletionStatsDto.GenreAbandonment::abandonmentRate).reversed()
                .thenComparing(Comparator.comparingLong(
                        (CompletionStatsDto.GenreAbandonment g) -> g.completedCount() + g.abandonedCount()).reversed())
                .thenComparing(CompletionStatsDto.GenreAbandonment::genreName, String.CASE_INSENSITIVE_ORDER));

        List<CompletionStatsDto.HoursBucket> hoursBuckets = new ArrayList<>();
        for (int i = 0; i < HOUR_BUCKETS.size(); i++) {
            HourRange range = HOUR_BUCKETS.get(i);
            List<BigDecimal> ratings = ratingsByHours.get(i);
            hoursBuckets.add(new CompletionStatsDto.HoursBucket(range.label(), range.min(), range.max(),
                    ratings.size(), ratings.stream().filter(Objects::nonNull).count(),
                    StatsMath.averageRating(ratings)));
        }

        return new CompletionStatsDto(year, completed, abandoned, replays,
                timeToFinish(userId, timed), abandonment, hoursBuckets);
    }

    private CompletionStatsDto.TimeToFinish timeToFinish(Long userId, List<TimedRun> timed) {
        List<CompletionStatsDto.DaysBucket> buckets = new ArrayList<>();
        for (DayRange range : DAY_BUCKETS) {
            long count = timed.stream().filter(t -> range.contains(t.days())).count();
            buckets.add(new CompletionStatsDto.DaysBucket(range.label(), range.min(), range.max(), count));
        }
        if (timed.isEmpty()) {
            return new CompletionStatsDto.TimeToFinish(0, null, null, null, null, buckets);
        }

        // Fewest days first; on equal days the lowest id first, so both ends break ties by lowest id.
        List<TimedRun> sorted = new ArrayList<>(timed);
        sorted.sort(Comparator.comparingLong(TimedRun::days).thenComparing(t -> t.run().getId()));
        TimedRun fastest = sorted.getFirst();
        long maxDays = sorted.getLast().days();
        TimedRun slowest = sorted.stream().filter(t -> t.days() == maxDays).findFirst().orElseThrow();

        int n = sorted.size();
        double median = n % 2 == 1
                ? sorted.get(n / 2).days()
                : (sorted.get(n / 2 - 1).days() + sorted.get(n / 2).days()) / 2.0;
        double average = sorted.stream().mapToLong(TimedRun::days).sum() / (double) n;

        Map<Long, String> names = new HashMap<>();
        for (GameLabel label : gameRepository.findLabelsByUserId(userId)) {
            names.put(label.id(), label.name());
        }
        return new CompletionStatsDto.TimeToFinish(n, round1(average), round1(median),
                toDuration(fastest, names), toDuration(slowest, names), buckets);
    }

    private static CompletionStatsDto.RunDuration toDuration(TimedRun timed, Map<Long, String> names) {
        Experience run = timed.run();
        return new CompletionStatsDto.RunDuration(run.getId(), run.getGameId(),
                names.getOrDefault(run.getGameId(), "Unknown"), run.getRunLabel(), timed.days());
    }

    // ---------------------------------------------------------------- rating profile

    /** Histogram step: half points, since ratings cluster high and are often given as x.5. */
    private static final BigDecimal RATING_BUCKET_SIZE = new BigDecimal("0.5");
    /** 20 half-point buckets below 10, plus one for perfect 10s. */
    private static final int RATING_BUCKETS = 21;

    @Override
    @Transactional(readOnly = true)
    public RatingStatsDto getRatings(Long userId, Integer year) {
        requireValidYearIfPresent(year);
        List<Experience> rated = runsInScope(userId, year).stream()
                .filter(run -> run.getRating() != null)
                .toList();
        Map<Long, List<GameGenreRow>> genresByGame = confirmedGenresByGame(userId);

        long[] counts = new long[RATING_BUCKETS];
        List<BigDecimal> ratings = new ArrayList<>(rated.size());
        Map<Long, List<BigDecimal>> byGenre = new LinkedHashMap<>();
        Map<Long, String> genreNames = new HashMap<>();
        Map<Long, List<BigDecimal>> byPlatform = new LinkedHashMap<>();
        Map<Long, Platform> platforms = new HashMap<>();
        for (Experience run : rated) {
            BigDecimal rating = run.getRating();
            ratings.add(rating);
            counts[ratingBucketOf(rating)]++;
            for (GameGenreRow genre : genresByGame.getOrDefault(run.getGameId(), List.of())) {
                genreNames.put(genre.genreId(), genre.genreName());
                byGenre.computeIfAbsent(genre.genreId(), id -> new ArrayList<>()).add(rating);
            }
            Platform platform = run.getPlatform();
            platforms.put(platform.getId(), platform);
            byPlatform.computeIfAbsent(platform.getId(), id -> new ArrayList<>()).add(rating);
        }

        List<RatingStatsDto.RatingBucket> histogram = new ArrayList<>(RATING_BUCKETS);
        for (int i = 0; i < RATING_BUCKETS; i++) {
            BigDecimal from = RATING_BUCKET_SIZE.multiply(BigDecimal.valueOf(i));
            BigDecimal to = i < RATING_BUCKETS - 1 ? from.add(RATING_BUCKET_SIZE) : from;
            histogram.add(new RatingStatsDto.RatingBucket(Ratings.normalize(from), Ratings.normalize(to), counts[i]));
        }

        List<RatingStatsDto.GenreRating> genres = new ArrayList<>();
        byGenre.forEach((genreId, values) -> genres.add(new RatingStatsDto.GenreRating(
                genreId, genreNames.get(genreId), values.size(), StatsMath.averageRating(values))));
        genres.sort(Comparator
                .comparing(RatingStatsDto.GenreRating::averageRating, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingLong(RatingStatsDto.GenreRating::ratedCount).reversed())
                .thenComparing(RatingStatsDto.GenreRating::genreName, String.CASE_INSENSITIVE_ORDER));

        List<RatingStatsDto.PlatformRating> platformRatings = new ArrayList<>();
        byPlatform.forEach((platformId, values) -> {
            Platform platform = platforms.get(platformId);
            platformRatings.add(new RatingStatsDto.PlatformRating(platformId, platform.getName(),
                    PlatformMapper.colorOf(platform), values.size(), StatsMath.averageRating(values)));
        });
        platformRatings.sort(Comparator
                .comparing(RatingStatsDto.PlatformRating::averageRating, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingLong(RatingStatsDto.PlatformRating::ratedCount).reversed())
                .thenComparing(RatingStatsDto.PlatformRating::name, String.CASE_INSENSITIVE_ORDER));

        return new RatingStatsDto(year, RATING_BUCKET_SIZE, ratings.size(),
                StatsMath.averageRating(ratings), StatsMath.medianRating(ratings), StatsMath.standardDeviation(ratings),
                histogram, genres, platformRatings);
    }

    /** Half-point bucket index: 9.75 -> 19 ([9.5, 10)), 10 -> 20 (perfect scores). */
    static int ratingBucketOf(BigDecimal rating) {
        int index = rating.multiply(BigDecimal.TWO).setScale(0, RoundingMode.FLOOR).intValue();
        return Math.min(Math.max(index, 0), RATING_BUCKETS - 1);
    }

    // ---------------------------------------------------------------- shared loaders

    /** Every run of the user, or only those of {@code year} (ExperiencePeriod year rule). */
    private List<Experience> runsInScope(Long userId, Integer year) {
        List<Experience> runs = experienceRepository.findAllByUserId(userId);
        if (year == null) {
            return runs;
        }
        return runs.stream().filter(run -> year.equals(ExperiencePeriod.yearOf(run))).toList();
    }

    /** Genres of the CONFIRMED games, per game id (games without genres are absent). */
    private Map<Long, List<GameGenreRow>> confirmedGenresByGame(Long userId) {
        Map<Long, List<GameGenreRow>> byGame = new HashMap<>();
        for (GameGenreRow row : gameRepository.findGenreRowsByUserIdAndReviewStatus(userId, ReviewStatus.CONFIRMED)) {
            byGame.computeIfAbsent(row.gameId(), id -> new ArrayList<>()).add(row);
        }
        return byGame;
    }

    // ---------------------------------------------------------------- helpers

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

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
