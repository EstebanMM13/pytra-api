package com.estebanmm13.pytra_api.obsidian.service;

import com.estebanmm13.pytra_api.error.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-instance throttle for the Obsidian export: at most {@value #MAX_EXPORTS} exports per key
 * (one personal access token, or one user when called with a session JWT) in each fixed one-minute window.
 * Same approach as ExportRateLimiter (single-instance deployment).
 */
@Component
public class ObsidianExportRateLimiter {

    static final int MAX_EXPORTS = 30;
    static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public ObsidianExportRateLimiter() {
        this(Clock.systemUTC());
    }

    ObsidianExportRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** @throws TooManyRequestsException OBSIDIAN_EXPORT_RATE_LIMITED when the key is over the limit */
    public void acquire(String key) {
        long now = clock.millis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> window.isExpired(now));
        }
        Window window = windows.compute(key, (k, current) ->
                current == null || current.isExpired(now) ? new Window(now, 1) : current.increment());
        if (window.count() > MAX_EXPORTS) {
            throw new TooManyRequestsException(TooManyRequestsException.OBSIDIAN_EXPORT_RATE_LIMITED,
                    WINDOW.toSeconds());
        }
    }

    private record Window(long startMillis, int count) {
        boolean isExpired(long now) {
            return now - startMillis >= WINDOW.toMillis();
        }

        Window increment() {
            return new Window(startMillis, count + 1);
        }
    }
}
