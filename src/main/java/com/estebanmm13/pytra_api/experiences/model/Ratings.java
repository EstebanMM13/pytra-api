package com.estebanmm13.pytra_api.experiences.model;

import java.math.BigDecimal;

/**
 * Ratings are 0..10 with up to 2 decimals, stored as NUMERIC(4,2). Postgres hands them back with a
 * fixed scale (9 -> 9.00); {@link #normalize} drops that noise so JSON and exports show 9, 9.5, 9.25.
 */
public final class Ratings {

    private Ratings() {
    }

    /** Strips trailing zeros without switching to scientific notation (10.00 -> 10, not 1E+1). */
    public static BigDecimal normalize(BigDecimal rating) {
        if (rating == null) {
            return null;
        }
        BigDecimal stripped = rating.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
