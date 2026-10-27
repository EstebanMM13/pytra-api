package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.experiences.model.Ratings;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Small numeric helpers shared by the stats endpoints. Rating math is exact (BigDecimal, no doubles). */
public final class StatsMath {

    private StatsMath() {
    }

    /** Mean of the non-null ratings rounded to 2 decimals (half up), or null when there are none. */
    public static BigDecimal averageRating(Collection<BigDecimal> ratings) {
        long count = 0;
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal rating : ratings) {
            if (rating != null) {
                count++;
                sum = sum.add(rating);
            }
        }
        return average(sum, count);
    }

    /** Null-tolerant sum of two exact sums (an aggregate SUM is null when nothing matched). */
    public static BigDecimal sum(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.add(b);
    }

    /** Hours (or any double figure) rounded to 2 decimals, half up, to hide floating-point noise of splits. */
    public static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Median of the non-null ratings (mean of the two middle ones for an even count), rounded to 2 decimals
     * (half up), or null when there are none.
     */
    public static BigDecimal medianRating(Collection<BigDecimal> ratings) {
        List<BigDecimal> sorted = nonNullSorted(ratings);
        int n = sorted.size();
        if (n == 0) {
            return null;
        }
        BigDecimal median = n % 2 == 1
                ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.TWO);
        return Ratings.normalize(median.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Population standard deviation of the non-null ratings (around their exact mean), rounded to 2 decimals
     * (half up): 0 for a single rating, null when there are none.
     */
    public static BigDecimal standardDeviation(Collection<BigDecimal> ratings) {
        List<BigDecimal> values = nonNullSorted(ratings);
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal count = BigDecimal.valueOf(values.size());
        BigDecimal mean = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(count, MathContext.DECIMAL64);
        BigDecimal squares = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            BigDecimal deviation = value.subtract(mean);
            squares = squares.add(deviation.multiply(deviation));
        }
        BigDecimal variance = squares.divide(count, MathContext.DECIMAL64);
        return Ratings.normalize(variance.sqrt(MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_UP));
    }

    private static List<BigDecimal> nonNullSorted(Collection<BigDecimal> ratings) {
        List<BigDecimal> values = new ArrayList<>();
        for (BigDecimal rating : ratings) {
            if (rating != null) {
                values.add(rating);
            }
        }
        values.sort(null);
        return values;
    }

    /** {@code sum / count} rounded to 2 decimals (half up) without trailing zeros, or null when count is 0. */
    public static BigDecimal average(BigDecimal sum, long count) {
        if (count == 0 || sum == null) {
            return null;
        }
        return Ratings.normalize(sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
    }
}
