package com.estebanmm13.pytra_api.account.service;

import com.estebanmm13.pytra_api.error.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-instance throttle for the full data export (it loads the whole library): at most
 * {@value #MAX_EXPORTS} exports per user in each fixed one-minute window. Same approach as
 * AuthRateLimitFilter, keyed by user id instead of IP (single-instance deployment).
 */
@Component
public class ExportRateLimiter {

    static final int MAX_EXPORTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<Long, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public ExportRateLimiter() {
        this(Clock.systemUTC());
    }

    ExportRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** @throws TooManyRequestsException EXPORT_RATE_LIMITED when the user is over the limit */
    public void acquire(Long userId) {
        long now = clock.millis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> window.isExpired(now));
        }
        Window window = windows.compute(userId, (id, current) ->
                current == null || current.isExpired(now) ? new Window(now, 1) : current.increment());
        if (window.count() > MAX_EXPORTS) {
            throw new TooManyRequestsException(TooManyRequestsException.EXPORT_RATE_LIMITED, WINDOW.toSeconds());
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
