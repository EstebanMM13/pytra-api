package com.estebanmm13.pytra_api.account.service;

import com.estebanmm13.pytra_api.error.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-09T10:00:00Z"));
    private final ExportRateLimiter limiter = new ExportRateLimiter(clock);

    @Test
    void allowsFiveExportsPerMinuteThenAnswers429() {
        for (int i = 0; i < ExportRateLimiter.MAX_EXPORTS; i++) {
            limiter.acquire(1L);
        }
        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage(TooManyRequestsException.EXPORT_RATE_LIMITED);
    }

    @Test
    void limitIsPerUser() {
        for (int i = 0; i < ExportRateLimiter.MAX_EXPORTS; i++) {
            limiter.acquire(1L);
        }
        assertThatCode(() -> limiter.acquire(2L)).doesNotThrowAnyException();
    }

    @Test
    void windowResetsAfterOneMinute() {
        for (int i = 0; i < ExportRateLimiter.MAX_EXPORTS; i++) {
            limiter.acquire(1L);
        }
        clock.advance(ExportRateLimiter.WINDOW);
        assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

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
