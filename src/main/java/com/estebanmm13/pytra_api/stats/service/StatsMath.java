package com.estebanmm13.pytra_api.stats.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/** Small numeric helpers shared by the stats endpoints. */
public final class StatsMath {

    private StatsMath() {
    }

    /** Mean of the non-null ratings rounded to 2 decimals (half up), or null when there are none. */
    public static Double averageRating(Collection<Integer> ratings) {
        long count = 0;
        long sum = 0;
        for (Integer rating : ratings) {
            if (rating != null) {
                count++;
                sum += rating;
            }
        }
        return count == 0 ? null : round2((double) sum / count);
    }

    public static Double round2(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
