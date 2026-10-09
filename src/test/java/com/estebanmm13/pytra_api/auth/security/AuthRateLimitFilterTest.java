package com.estebanmm13.pytra_api.auth.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRateLimitFilterTest {

    @Test
    void allowsFiveRequestsPerWindowPerKeyThenResets() {
        MutableClock clock = new MutableClock();
        AuthRateLimitFilter filter = new AuthRateLimitFilter(clock);

        for (int i = 0; i < AuthRateLimitFilter.MAX_REQUESTS; i++) {
            assertTrue(filter.tryAcquire("/login|1.1.1.1"));
        }
        assertFalse(filter.tryAcquire("/login|1.1.1.1"));
        assertTrue(filter.tryAcquire("/login|2.2.2.2"), "other IPs are independent");
        assertTrue(filter.tryAcquire("/register|1.1.1.1"), "other endpoints are independent");

        clock.advance(AuthRateLimitFilter.WINDOW);
        assertTrue(filter.tryAcquire("/login|1.1.1.1"));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
