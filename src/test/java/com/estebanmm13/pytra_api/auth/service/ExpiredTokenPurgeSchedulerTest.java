package com.estebanmm13.pytra_api.auth.service;

import com.estebanmm13.pytra_api.auth.repository.EmailVerificationTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.ExchangeCodeTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpiredTokenPurgeSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 10, 11, 12, 0);

    private final EmailVerificationTokenRepository emailTokens = mock(EmailVerificationTokenRepository.class);
    private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
    private final ExchangeCodeTokenRepository exchangeCodes = mock(ExchangeCodeTokenRepository.class);
    private final ExpiredTokenPurgeScheduler scheduler = new ExpiredTokenPurgeScheduler(
            emailTokens, resetTokens, exchangeCodes, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void retentionOutlivesEveryTokenTtl() {
        // Longest TTL is the 24 h email verification link.
        assertThat(ExpiredTokenPurgeScheduler.RETENTION).isGreaterThan(Duration.ofHours(24));
    }

    @Test
    void purgesEveryTokenTableWithTheRetentionCutoff() {
        scheduler.purge();

        verify(emailTokens).deleteExpiredOrConsumedBefore(CUTOFF);
        verify(resetTokens).deleteExpiredOrConsumedBefore(CUTOFF);
        verify(exchangeCodes).deleteExpiredOrConsumedBefore(CUTOFF);
    }

    @Test
    void aFailingTableDoesNotStopTheOthers() {
        when(emailTokens.deleteExpiredOrConsumedBefore(any())).thenThrow(new QueryTimeoutException("timeout"));
        when(resetTokens.deleteExpiredOrConsumedBefore(any())).thenReturn(3);

        assertThatNoException().isThrownBy(scheduler::purge);

        verify(resetTokens).deleteExpiredOrConsumedBefore(CUTOFF);
        verify(exchangeCodes).deleteExpiredOrConsumedBefore(CUTOFF);
    }
}
