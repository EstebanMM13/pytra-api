package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.RunDiaryDayHours;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * When each run's hours were played, so the per-year and per-month hours follow the calendar instead of
 * landing all at once on the credits.
 * <ul>
 *   <li>Diary lines that counted into the run keep their own day; when they add up to more than the run's
 *       hours (the hours were edited down) they are scaled to fit.</li>
 *   <li>The rest is spread evenly per day from the start to the latest of the credits and the end of the
 *       postgame; while the run is being played (in progress, postgame going on) the period runs until
 *       today. A run with a single date puts the rest on that day.</li>
 *   <li>A run without dates keeps the old rule: {@link ExperiencePeriod#yearOf}, without a month; with no
 *       year either, its hours are left out of every per-year figure.</li>
 * </ul>
 * Counts, ratings and GOTY are not hours: they keep {@link ExperiencePeriod#yearOf} (the credits).
 */
final class RunHours {

    /** Hours of a run in one month of a year; {@code month} null when only the year is known. */
    record Slice(int year, Integer month, double hours) {
    }

    private final Map<Experience, List<Slice>> slicesByRun = new IdentityHashMap<>();

    private RunHours() {
    }

    /**
     * @param runs  the user's runs
     * @param diary the user's counted diary hours per run and day
     * @param today the users' current day, the end of a period still being played
     */
    static RunHours of(Collection<Experience> runs, Collection<RunDiaryDayHours> diary, LocalDate today) {
        Map<Long, Map<LocalDate, Double>> diaryByRun = new HashMap<>();
        for (RunDiaryDayHours row : diary) {
            double hours = row.hours() != null ? row.hours().doubleValue() : 0;
            if (row.experienceId() != null && row.day() != null && hours > 0) {
                diaryByRun.computeIfAbsent(row.experienceId(), id -> new HashMap<>()).merge(row.day(), hours, Double::sum);
            }
        }
        RunHours result = new RunHours();
        for (Experience run : runs) {
            Map<LocalDate, Double> days = run.getId() != null ? diaryByRun.getOrDefault(run.getId(), Map.of()) : Map.of();
            result.slicesByRun.put(run, slices(run, days, today));
        }
        return result;
    }

    static List<Slice> slices(Experience run, Map<LocalDate, Double> diaryDays, LocalDate today) {
        double total = run.getHours() != null ? run.getHours() : 0;
        if (total <= 0) {
            return List.of();
        }
        Map<YearMonth, Double> byMonth = new HashMap<>();
        double diaryTotal = diaryDays.values().stream().mapToDouble(Double::doubleValue).sum();
        double scale = diaryTotal > total ? total / diaryTotal : 1;
        diaryDays.forEach((day, hours) -> byMonth.merge(YearMonth.from(day), hours * scale, Double::sum));
        double rest = total - diaryTotal * scale;

        List<Slice> result = new ArrayList<>();
        if (rest > 0) {
            LocalDate start = run.getStartDate();
            LocalDate end = latest(run.getEndDate(), run.getPostgameEndDate());
            if (run.isBeingPlayed() && start != null) {
                end = latest(end, today);
            }
            if (start == null) {
                start = end;
            }
            if (end == null || end.isBefore(start)) {
                end = start;
            }
            if (start == null) {
                Integer year = ExperiencePeriod.yearOf(run);
                if (year != null) {
                    result.add(new Slice(year, null, rest));
                }
            } else {
                spread(rest, start, end, byMonth);
            }
        }
        byMonth.forEach((month, hours) -> result.add(new Slice(month.getYear(), month.getMonthValue(), hours)));
        return result;
    }

    /** Spreads {@code hours} evenly over the days from {@code start} to {@code end}, both included. */
    private static void spread(double hours, LocalDate start, LocalDate end, Map<YearMonth, Double> byMonth) {
        double perDay = hours / (ChronoUnit.DAYS.between(start, end) + 1);
        YearMonth month = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!month.isAfter(last)) {
            LocalDate from = month.equals(YearMonth.from(start)) ? start : month.atDay(1);
            LocalDate to = month.equals(last) ? end : month.atEndOfMonth();
            byMonth.merge(month, perDay * (ChronoUnit.DAYS.between(from, to) + 1), Double::sum);
            month = month.plusMonths(1);
        }
    }

    private static LocalDate latest(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    List<Slice> slicesOf(Experience run) {
        return slicesByRun.getOrDefault(run, List.of());
    }

    /** Hours of one run played in {@code year}. */
    double inYear(Experience run, int year) {
        double total = 0;
        for (Slice slice : slicesOf(run)) {
            if (slice.year() == year) total += slice.hours();
        }
        return total;
    }

    /** Hours of every run played in {@code year}. */
    double inYear(int year) {
        double total = 0;
        for (Experience run : slicesByRun.keySet()) {
            total += inYear(run, year);
        }
        return total;
    }

    /** Hours of every run per month of {@code year} (index 0 = January); undated-month hours are left out. */
    double[] byMonth(int year) {
        double[] months = new double[12];
        for (List<Slice> slices : slicesByRun.values()) {
            for (Slice slice : slices) {
                if (slice.year() == year && slice.month() != null) months[slice.month() - 1] += slice.hours();
            }
        }
        return months;
    }

    /** Hours of runs known only by their year. */
    double withoutMonth(int year) {
        double total = 0;
        for (List<Slice> slices : slicesByRun.values()) {
            for (Slice slice : slices) {
                if (slice.year() == year && slice.month() == null) total += slice.hours();
            }
        }
        return total;
    }

    /** Every year with played hours (at least 0.01 h once rounded, so a long spread adds no empty years). */
    Set<Integer> years() {
        Map<Integer, Double> byYear = new HashMap<>();
        for (List<Slice> slices : slicesByRun.values()) {
            for (Slice slice : slices) {
                byYear.merge(slice.year(), slice.hours(), Double::sum);
            }
        }
        Set<Integer> years = new HashSet<>();
        byYear.forEach((year, hours) -> {
            if (StatsMath.round2(hours) > 0) years.add(year);
        });
        return years;
    }

    /** Hours per year of one run, oldest year first. */
    SortedMap<Integer, Double> byYear(Experience run) {
        SortedMap<Integer, Double> years = new TreeMap<>();
        for (Slice slice : slicesOf(run)) {
            years.merge(slice.year(), slice.hours(), Double::sum);
        }
        return years;
    }
}
