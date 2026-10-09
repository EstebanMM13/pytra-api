package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.experiences.model.Ratings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

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

    /** {@code sum / count} rounded to 2 decimals (half up) without trailing zeros, or null when count is 0. */
    public static BigDecimal average(BigDecimal sum, long count) {
        if (count == 0 || sum == null) {
            return null;
        }
        return Ratings.normalize(sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
    }
}
