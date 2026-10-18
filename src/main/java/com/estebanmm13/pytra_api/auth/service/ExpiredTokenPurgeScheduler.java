package com.estebanmm13.pytra_api.auth.service;

import com.estebanmm13.pytra_api.auth.repository.EmailVerificationTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.ExchangeCodeTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.PasswordResetTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Daily cleanup of the one-time token tables (email verification, password reset, exchange code), which
 * otherwise grow forever. Steam link states are purged by SteamLinkServiceImpl on each connect.
 */
@Slf4j
@Component
public class ExpiredTokenPurgeScheduler {

    /**
     * How long expired or consumed tokens are kept. Every TTL is at most 24 h; the grace period keeps a
     * recently expired link answering "Token expired" and a reopened verification link answering success,
     * instead of "Invalid token".
     */
    static final Duration RETENTION = Duration.ofDays(7);

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ExchangeCodeTokenRepository exchangeCodeTokenRepository;
    private final Clock clock;

    @Autowired
    public ExpiredTokenPurgeScheduler(EmailVerificationTokenRepository emailVerificationTokenRepository,
                                      PasswordResetTokenRepository passwordResetTokenRepository,
                                      ExchangeCodeTokenRepository exchangeCodeTokenRepository) {
        // Same zone as the LocalDateTime.now() the tokens are written with.
        this(emailVerificationTokenRepository, passwordResetTokenRepository, exchangeCodeTokenRepository,
                Clock.systemDefaultZone());
    }

    ExpiredTokenPurgeScheduler(EmailVerificationTokenRepository emailVerificationTokenRepository,
                               PasswordResetTokenRepository passwordResetTokenRepository,
                               ExchangeCodeTokenRepository exchangeCodeTokenRepository,
                               Clock clock) {
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.exchangeCodeTokenRepository = exchangeCodeTokenRepository;
        this.clock = clock;
    }

    /** Once a day; the 30-minute initial delay keeps it off the startup path of a deploy. */
    @Scheduled(initialDelay = 30, fixedRate = 1440, timeUnit = TimeUnit.MINUTES)
    public void purge() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(RETENTION);
        purge("email verification", cutoff, emailVerificationTokenRepository::deleteExpiredOrConsumedBefore);
        purge("password reset", cutoff, passwordResetTokenRepository::deleteExpiredOrConsumedBefore);
        purge("exchange code", cutoff, exchangeCodeTokenRepository::deleteExpiredOrConsumedBefore);
    }

    /** Each table in its own transaction: one failing never keeps the others from being purged. */
    private static void purge(String table, LocalDateTime cutoff, Function<LocalDateTime, Integer> delete) {
        try {
            int deleted = delete.apply(cutoff);
            if (deleted > 0) {
                log.info("Purged {} {} tokens expired or consumed before {}", deleted, table, cutoff);
            }
        } catch (Exception e) {
            log.warn("Purging {} tokens failed: {}", table, e.getClass().getSimpleName());
        }
    }
}
