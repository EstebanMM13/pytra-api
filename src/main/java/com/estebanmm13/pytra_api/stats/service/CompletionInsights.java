package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.games.repository.GameGenreRow;
import com.estebanmm13.pytra_api.games.repository.GameLabel;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.stats.dto.CompletionStatsDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.estebanmm13.pytra_api.stats.service.InsightMath.hoursOf;
import static com.estebanmm13.pytra_api.stats.service.InsightMath.requireValidYearIfPresent;
import static com.estebanmm13.pytra_api.stats.service.InsightMath.round1;

/** Completion habits: outcomes, time to finish, abandonment by genre and ratings by run length. */
final class CompletionInsights {

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

    private final InsightSources sources;
    private final GameRepository gameRepository;

    CompletionInsights(InsightSources sources, GameRepository gameRepository) {
        this.sources = sources;
        this.gameRepository = gameRepository;
    }

    CompletionStatsDto completion(Long userId, Integer year) {
        requireValidYearIfPresent(year);
        List<Experience> runs = sources.runsInScope(userId, year);
        Map<Long, List<GameGenreRow>> genresByGame = sources.confirmedGenresByGame(userId);

        long completed = 0;
        long abandoned = 0;
        long replays = 0;
        List<TimedRun> timed = new ArrayList<>();
        Map<Long, long[]> outcomesByGenre = new LinkedHashMap<>(); // [completed, abandoned]
        Map<Long, String> genreNames = new HashMap<>();
        List<List<BigDecimal>> ratingsByHours = new ArrayList<>();
        List<Set<Long>> gamesByHours = new ArrayList<>();
        HOUR_BUCKETS.forEach(bucket -> {
            ratingsByHours.add(new ArrayList<>());
            gamesByHours.add(new HashSet<>());
        });

        for (Experience run : runs) {
            if (Boolean.TRUE.equals(run.getReplay())) replays++;
            boolean isCompleted = run.getStatus() == ExperienceStatus.COMPLETADO;
            boolean isAbandoned = run.getStatus() == ExperienceStatus.ABANDONADO;
            if (isCompleted) {
                completed++;
                Long days = daysToFinish(run);
                if (days != null) {
                    timed.add(new TimedRun(run, days));
                }
                for (int i = 0; i < HOUR_BUCKETS.size(); i++) {
                    if (HOUR_BUCKETS.get(i).contains(hoursOf(run))) {
                        ratingsByHours.get(i).add(run.getRating());
                        gamesByHours.get(i).add(run.getGameId());
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
                    StatsMath.averageRating(ratings), gamesByHours.get(i).size()));
        }

        return new CompletionStatsDto(year, completed, abandoned, replays,
                timeToFinish(userId, timed), abandonment, hoursBuckets);
    }

    private CompletionStatsDto.TimeToFinish timeToFinish(Long userId, List<TimedRun> timed) {
        List<CompletionStatsDto.DaysBucket> buckets = new ArrayList<>();
        for (DayRange range : DAY_BUCKETS) {
            List<TimedRun> inRange = timed.stream().filter(t -> range.contains(t.days())).toList();
            long games = inRange.stream().map(t -> t.run().getGameId()).distinct().count();
            buckets.add(new CompletionStatsDto.DaysBucket(range.label(), range.min(), range.max(), inRange.size(), games));
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

    /**
     * Calendar days from start to end of a COMPLETADO run with both dates, end not before start (0 when started
     * and finished the same day); null for any other run. Shared by the completion stats and the play facts.
     */
    static Long daysToFinish(Experience run) {
        if (run.getStatus() != ExperienceStatus.COMPLETADO || run.getStartDate() == null || run.getEndDate() == null
                || run.getEndDate().isBefore(run.getStartDate())) {
            return null;
        }
        return ChronoUnit.DAYS.between(run.getStartDate(), run.getEndDate());
    }
}
