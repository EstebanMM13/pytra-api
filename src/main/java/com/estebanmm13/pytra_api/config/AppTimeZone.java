package com.estebanmm13.pytra_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;

/**
 * The time zone the app's users live in ({@code app.time-zone}, default Europe/Madrid). Calendar days the
 * user sees (diary days written by the sync, "today" in the activity streaks) are taken in this zone, not
 * in the server's (UTC on Railway), so late-night play lands on the right day.
 */
@Component
public class AppTimeZone {

    private final ZoneId zone;

    public AppTimeZone(@Value("${app.time-zone:Europe/Madrid}") String zoneId) {
        this(ZoneId.of(zoneId.trim()));
    }

    private AppTimeZone(ZoneId zone) {
        this.zone = zone;
    }

    public static AppTimeZone of(ZoneId zone) {
        return new AppTimeZone(zone);
    }

    public ZoneId zone() {
        return zone;
    }

    /** The current instant, read in the app's zone. */
    public Clock clock() {
        return Clock.system(zone);
    }
}
