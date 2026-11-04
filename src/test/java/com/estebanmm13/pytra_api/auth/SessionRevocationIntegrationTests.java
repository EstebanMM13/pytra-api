package com.estebanmm13.pytra_api.auth;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.model.ExchangeCodeToken;
import com.estebanmm13.pytra_api.auth.model.PasswordResetToken;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.ExchangeCodeTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.PasswordResetTokenRepository;
import com.estebanmm13.pytra_api.auth.security.TokenGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A password reset ends earlier sessions; one-time tokens can be consumed only once. */
class SessionRevocationIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private ExchangeCodeTokenRepository exchangeCodeTokenRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    private String issueResetToken(User user) {
        String raw = tokenGenerator.generateTokenRaw();
        passwordResetTokenRepository.save(PasswordResetToken.builder().user(user)
                .tokenHash(tokenGenerator.hashToken(raw)).expiresAt(LocalDateTime.now().plusMinutes(30)).build());
        return raw;
    }

    private void resetPassword(String rawToken, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"brand-new-password\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void passwordResetRevokesEarlierJwtsButNotLaterOnes() throws Exception {
        User user = createVerifiedUser("revoke");
        String oldSession = bearer(user);
        performAs(oldSession, get("/api/v1/users/me")).andExpect(status().isOk());
        // JWT iat has second precision: move past the second the old token was minted in.
        Thread.sleep(1100);

        resetPassword(issueResetToken(user), 204);

        performAs(oldSession, get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        performAs(bearer(user), get("/api/v1/users/me")).andExpect(status().isOk());
    }

    @Test
    void tokenStillValidComparesTheCutOffWithTheIssueTime() {
        User user = createVerifiedUser("cutoff");
        Instant cutOff = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        assertThat(userRepository.isTokenStillValid(user.getId(), cutOff.minusSeconds(1))).isTrue();
        user.setCredentialsChangedAt(cutOff);
        userRepository.save(user);

        assertThat(userRepository.isTokenStillValid(user.getId(), cutOff.minusSeconds(1))).isFalse();
        assertThat(userRepository.isTokenStillValid(user.getId(), cutOff)).isTrue();
        assertThat(userRepository.isTokenStillValid(user.getId(), null)).isFalse();
        assertThat(userRepository.isTokenStillValid(-1L, cutOff)).isFalse();
    }

    @Test
    void resetTokenWorksOnlyOnce() throws Exception {
        String raw = issueResetToken(createVerifiedUser("reuse"));

        resetPassword(raw, 204);
        resetPassword(raw, 400);
    }

    @Test
    void exchangeCodeCanBeMarkedConsumedOnlyOnce() {
        ExchangeCodeToken code = exchangeCodeTokenRepository.save(ExchangeCodeToken.builder()
                .user(createVerifiedUser("exchange")).tokenHash(tokenGenerator.hashToken(tokenGenerator.generateTokenRaw()))
                .expiresAt(LocalDateTime.now().plusMinutes(1)).build());

        assertThat(exchangeCodeTokenRepository.markConsumed(code.getId(), LocalDateTime.now())).isEqualTo(1);
        assertThat(exchangeCodeTokenRepository.markConsumed(code.getId(), LocalDateTime.now())).isZero();
    }
}
