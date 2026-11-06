package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.experiences.model.Experience;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Small rules shared by the stats insights. */
final class InsightMath {

    private static final int MAX_LIMIT = 50;

    private InsightMath() {
    }

    static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    static void requireValidYearIfPresent(Integer year) {
        if (year != null) {
            StatsServiceImpl.requireValidYear(year);
        }
    }

    static double hoursOf(Experience experience) {
        return experience.getHours() != null ? experience.getHours() : 0.0;
    }

    static double doubleOf(Number value) {
        return value != null ? value.doubleValue() : 0.0;
    }
}
