package com.estebanmm13.pytra_api.experiences.validation;

import java.time.LocalDate;

/**
 * Sanity bounds for the dates of a run: not before {@link #EARLIEST} and at most one day after the
 * server's today (one day of slack so a user ahead of the server's time zone can log today's run).
 */
public final class RunDateLimits {

    public static final LocalDate EARLIEST = LocalDate.of(1970, 1, 1);

    private RunDateLimits() {
    }

    public static LocalDate latest() {
        return LocalDate.now().plusDays(1);
    }

    public static boolean isWithin(LocalDate date) {
        return date == null || (!date.isBefore(EARLIEST) && !date.isAfter(latest()));
    }

    public static boolean isWithin(Integer year) {
        return year == null || (year >= EARLIEST.getYear() && year <= latest().getYear());
    }
}
