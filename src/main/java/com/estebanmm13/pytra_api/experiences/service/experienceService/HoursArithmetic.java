package com.estebanmm13.pytra_api.experiences.service.experienceService;

import java.math.BigDecimal;

/** Hour sums for the stored {@code Double} totals, done in decimal so repeated changes never drift. */
public final class HoursArithmetic {

    private HoursArithmetic() {
    }

    /** {@code current + delta}, never below 0. A null current counts as 0. */
    public static double add(Double current, BigDecimal delta) {
        BigDecimal base = current == null ? BigDecimal.ZERO : BigDecimal.valueOf(current);
        BigDecimal sum = base.add(delta);
        return sum.signum() < 0 ? 0.0 : sum.doubleValue();
    }
}
