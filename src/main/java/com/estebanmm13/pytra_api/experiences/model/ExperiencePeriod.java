package com.estebanmm13.pytra_api.experiences.model;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Single source of truth for "when" an experience happened, shared by the yearly stats, the
 * year summary and the library aggregates so they always agree.
 *
 * <ul>
 *   <li><b>Year</b>: the explicit {@code year} field wins (it is what the user typed); otherwise the
 *       year of {@code endDate}; otherwise the year of {@code startDate}; otherwise no year (the
 *       experience is left out of every per-year stat).</li>
 *   <li><b>Month</b>: the month of {@code endDate} when it falls in that year; otherwise the month of
 *       {@code startDate} when it falls in that year; otherwise no month. All the hours of a run are
 *       attributed to that single month.</li>
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
