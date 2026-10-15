package com.estebanmm13.pytra_api.platforms.service;

import com.estebanmm13.pytra_api.error.ResourceLimitExceededException;
import com.estebanmm13.pytra_api.error.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformCreationGuardTest {

    private static final int MAX_PER_WINDOW = 3;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-15T10:00:00Z"));
    private final PlatformCreationGuard guard = new PlatformCreationGuard(MAX_PER_WINDOW, WINDOW, 200, clock);

    @Test
    void allowsTheConfiguredCreatesPerWindowThenAnswers429() {
        for (int i = 0; i < MAX_PER_WINDOW; i++) {
            guard.acquire(1L);
        }
        clock.advance(Duration.ofMinutes(15));

        assertThatThrownBy(() -> guard.acquire(1L))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage(TooManyRequestsException.PLATFORM_RATE_LIMITED)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds())
                        .isEqualTo(Duration.ofMinutes(45).toSeconds()));
    }

    @Test
    void limitIsPerUser() {
        for (int i = 0; i < MAX_PER_WINDOW; i++) {
            guard.acquire(1L);
        }
        assertThatCode(() -> guard.acquire(2L)).doesNotThrowAnyException();
    }

    @Test
    void windowResets() {
        for (int i = 0; i < MAX_PER_WINDOW; i++) {
            guard.acquire(1L);
        }
        clock.advance(WINDOW);
        assertThatCode(() -> guard.acquire(1L)).doesNotThrowAnyException();
    }

    @Test
    void capacityIsRejectedOnceTheCatalogReachesTheCap() {
        assertThatCode(() -> guard.checkCapacity(199)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkCapacity(200))
                .isInstanceOf(ResourceLimitExceededException.class)
                .hasMessage(ResourceLimitExceededException.PLATFORM_CATALOG_FULL);
    }

    @Test
    void nonPositiveLimitsAreRejected() {
        assertThatThrownBy(() -> new PlatformCreationGuard(0, WINDOW, 200, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformCreationGuard(1, Duration.ZERO, 200, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformCreationGuard(1, WINDOW, 0, clock))
                .isInstanceOf(IllegalArgumentException.class);
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
