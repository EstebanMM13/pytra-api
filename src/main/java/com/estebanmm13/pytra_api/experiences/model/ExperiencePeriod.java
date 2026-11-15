package com.estebanmm13.pytra_api.experiences.model;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Single source of truth for which year a run belongs to (its credits), shared by the yearly counts, the
 * year summary's run table and the library aggregates so they always agree. The hours played per year and
 * month do not use it: they follow the days played ({@code stats.service.RunHours}).
 *
 * <ul>
 *   <li><b>Year</b>: the explicit {@code year} field wins (it is what the user typed); otherwise the
 *       year of {@code endDate}; otherwise the year of {@code startDate}; otherwise no year (the
 *       experience is left out of every per-year stat).</li>
 *   <li><b>Month</b>: the month of {@code endDate} when it falls in that year; otherwise the month of
 *       {@code startDate} when it falls in that year; otherwise no month. It is the month shown
 *       on the run in the year table.</li>
 *   <li><b>Recency</b> (to pick a game's latest run): {@code endDate}, else {@code startDate}, else
 *       January 1st of {@code year}; runs without any of them are the oldest, ties broken by id.</li>
 * </ul>
 */
public final class ExperiencePeriod {

    /** Most recent run last. */
    public static final Comparator<Experience> RECENCY = Comparator
            .comparing(ExperiencePeriod::recencyDateOf, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Experience::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

    private ExperiencePeriod() {
    }

    public static Integer yearOf(Experience experience) {
        return yearOf(experience.getYear(), experience.getStartDate(), experience.getEndDate());
    }

    public static Integer yearOf(Integer year, LocalDate startDate, LocalDate endDate) {
        if (year != null) {
            return year;
        }
        if (endDate != null) {
            return endDate.getYear();
        }
        if (startDate != null) {
            return startDate.getYear();
        }
        return null;
    }

    /** Month 1-12 inside {@link #yearOf(Experience)}, or null when no date falls in that year. */
    public static Integer monthOf(Experience experience) {
        return monthOf(experience.getYear(), experience.getStartDate(), experience.getEndDate());
    }

    public static Integer monthOf(Integer year, LocalDate startDate, LocalDate endDate) {
        Integer effectiveYear = yearOf(year, startDate, endDate);
        if (effectiveYear == null) {
            return null;
        }
        if (endDate != null && endDate.getYear() == effectiveYear) {
            return endDate.getMonthValue();
        }
        if (startDate != null && startDate.getYear() == effectiveYear) {
            return startDate.getMonthValue();
        }
        return null;
    }

    /**
     * Year a run's platinum / 100% were earned: they have no date of their own, so the end of the postgame
     * stands for it, else the credits ({@link #yearOf}).
     */
    public static Integer achievementYearOf(Experience experience) {
        if (experience.getPostgameEndDate() != null) {
            return experience.getPostgameEndDate().getYear();
        }
        return yearOf(experience);
    }

    public static LocalDate recencyDateOf(Experience experience) {
        if (experience.getEndDate() != null) {
            return experience.getEndDate();
        }
        if (experience.getStartDate() != null) {
            return experience.getStartDate();
        }
        return experience.getYear() != null ? LocalDate.of(experience.getYear(), 1, 1) : null;
    }
}
