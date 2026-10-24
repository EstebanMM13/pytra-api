package com.estebanmm13.pytra_api.obsidian.service;

import com.estebanmm13.pytra_api.error.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObsidianExportRateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-24T10:00:00Z");

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

    private final MutableClock clock = new MutableClock();
    private final ObsidianExportRateLimiter limiter = new ObsidianExportRateLimiter(clock);

    @Test
    void allowsThirtyPerMinutePerKeyThenRefuses() {
        for (int i = 0; i < ObsidianExportRateLimiter.MAX_EXPORTS; i++) {
            limiter.acquire("token:1");
        }
        assertThatThrownBy(() -> limiter.acquire("token:1"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage(TooManyRequestsException.OBSIDIAN_EXPORT_RATE_LIMITED);

        // Keys are independent.
        assertThatCode(() -> limiter.acquire("token:2")).doesNotThrowAnyException();

        // A new window starts after a minute.
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.acquire("token:1")).doesNotThrowAnyException();
    }
}
