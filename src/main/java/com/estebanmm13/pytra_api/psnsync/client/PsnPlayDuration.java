package com.estebanmm13.pytra_api.psnsync.client;

import java.time.Duration;
import java.time.format.DateTimeParseException;

/** Parses PSN's {@code playDuration} (ISO-8601, hours as the largest unit: "PT228H56M33S"). */
public final class PsnPlayDuration {

    private PsnPlayDuration() {
    }

    /**
     * @return the duration rounded to the nearest minute (the unit Pytra stores synced playtime in);
     *         0 when the value is missing, malformed or negative
     */
    public static long toMinutes(String playDuration) {
        if (playDuration == null || playDuration.isBlank()) {
            return 0;
        }
        try {
            long seconds = Duration.parse(playDuration.strip()).getSeconds();
            return seconds <= 0 ? 0 : (seconds + 30) / 60;
        } catch (DateTimeParseException e) {
            return 0;
        }
    }
}
