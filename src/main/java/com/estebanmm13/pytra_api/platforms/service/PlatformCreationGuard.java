package com.estebanmm13.pytra_api.platforms.service;

import com.estebanmm13.pytra_api.error.ResourceLimitExceededException;
import com.estebanmm13.pytra_api.error.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abuse protection for platform creation. The catalog is global, so every entry a user creates shows up for
 * everyone: each user may create at most {@code maxCreatesPerWindow} new entries per fixed {@code window}
 * (in-memory, per instance, keyed by user id like ExportRateLimiter), and the catalog never grows past
 * {@code maxTotal} entries, seeded ones included.
 * <p>
 * Only real creations are counted: a create-if-missing call that resolves to an existing entry never reaches
 * this guard.
 */
@Component
public class PlatformCreationGuard {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxCreatesPerWindow;
    private final Duration window;
    private final long maxTotal;
    private final Clock clock;
    private final Map<Long, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public PlatformCreationGuard(@Value("${app.platforms.create-rate-limit.max-per-window:20}") int maxCreatesPerWindow,
                                 @Value("${app.platforms.create-rate-limit.window:1h}") Duration window,
                                 @Value("${app.platforms.max-total:200}") long maxTotal) {
        this(maxCreatesPerWindow, window, maxTotal, Clock.systemUTC());
    }

    PlatformCreationGuard(int maxCreatesPerWindow, Duration window, long maxTotal, Clock clock) {
        if (maxCreatesPerWindow <= 0 || window.isZero() || window.isNegative() || maxTotal <= 0) {
            throw new IllegalArgumentException("Platform creation limits must be positive");
        }
        this.maxCreatesPerWindow = maxCreatesPerWindow;
        this.window = window;
        this.maxTotal = maxTotal;
        this.clock = clock;
    }

    /**
     * Soft cap: the count and the insert are not atomic, so concurrent creations can overshoot it by a few
     * entries, which is fine for abuse protection (the per-user rate limit bounds how many).
     *
     * @throws ResourceLimitExceededException PLATFORM_CATALOG_FULL when the catalog already has maxTotal entries
     */
    public void checkCapacity(long currentTotal) {
        if (currentTotal >= maxTotal) {
            throw new ResourceLimitExceededException(ResourceLimitExceededException.PLATFORM_CATALOG_FULL);
        }
    }

    /** @throws TooManyRequestsException PLATFORM_RATE_LIMITED when the user is over the limit */
    public void acquire(Long userId) {
        long now = clock.millis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.isExpired(now, window));
        }
        Window current = windows.compute(userId, (id, w) ->
                w == null || w.isExpired(now, window) ? new Window(now, 1) : w.increment());
        if (current.count() > maxCreatesPerWindow) {
            long retryAfterMillis = current.startMillis() + window.toMillis() - now;
            throw new TooManyRequestsException(TooManyRequestsException.PLATFORM_RATE_LIMITED,
                    Math.max(1, (retryAfterMillis + 999) / 1000));
        }
    }

    private record Window(long startMillis, int count) {
        boolean isExpired(long now, Duration window) {
            return now - startMillis >= window.toMillis();
        }

        Window increment() {
            return new Window(startMillis, count + 1);
        }
    }
}
