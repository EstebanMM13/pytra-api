package com.estebanmm13.pytra_api.psnsync.client;

import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PsnAuthenticatorTest {

    private static final String NPSSO = "n".repeat(64);
    private static final String AUTHORIZE_URL = "https://ca.account.sony.com/api/authz/v3/oauth/authorize";
    private static final String TOKEN_URL = "https://ca.account.sony.com/api/authz/v3/oauth/token";
    private static final String CODE_REDIRECT = "com.scee.psxandroid.scecompcall://redirect/?code=v3.ABC123&cid=xyz";

    private MockRestServiceServer server;
    private MutableClock clock;
    private PsnAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = PsnAuthenticator.configure(RestClient.builder());
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        authenticator = new PsnAuthenticator(builder.build(), NPSSO, clock);
    }

    @Test
    void signsInFromNpssoThroughAuthorizeRedirectAndCodeExchange() {
        expectAuthorize();
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, PsnAuthenticator.CLIENT_BASIC_AUTH))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("code=v3.ABC123")))
                .andRespond(withSuccess(tokens("access-1", "refresh-1"), MediaType.APPLICATION_JSON));

        assertThat(authenticator.accessToken()).isEqualTo("access-1");
        server.verify();
        assertThat(authenticator.authFailingSince()).isNull();
    }

    @Test
    void reusesTheAccessTokenWhileItIsValid() {
        signIn("access-1", "refresh-1");

        clock.advance(Duration.ofMinutes(30));

        assertThat(authenticator.accessToken()).isEqualTo("access-1");
        server.verify();
    }

    @Test
    void refreshesAnExpiredAccessTokenWithTheRefreshToken() {
        signIn("access-1", "refresh-1");
        server.reset();
        server.expect(requestTo(TOKEN_URL))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andExpect(content().string(containsString("refresh_token=refresh-1")))
                .andRespond(withSuccess(tokens("access-2", "refresh-2"), MediaType.APPLICATION_JSON));

        clock.advance(Duration.ofHours(2));

        assertThat(authenticator.accessToken()).isEqualTo("access-2");
        server.verify();
    }

    @Test
    void fallsBackToNpssoWhenTheRefreshTokenIsRejected() {
        signIn("access-1", "refresh-1");
        server.reset();
        server.expect(requestTo(TOKEN_URL))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        expectAuthorize();
        server.expect(requestTo(TOKEN_URL))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andRespond(withSuccess(tokens("access-3", "refresh-3"), MediaType.APPLICATION_JSON));

        clock.advance(Duration.ofHours(2));

        assertThat(authenticator.accessToken()).isEqualTo("access-3");
        server.verify();
    }

    @Test
    void expiredNpssoIsReportedAsUnavailableAndFlaggedForTheAdmin() {
        server.expect(requestTo(startsWith(AUTHORIZE_URL)))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI.create(
                        "com.scee.psxandroid.scecompcall://redirect/?error=login_required&error_code=4165")));

        assertThatThrownBy(() -> authenticator.accessToken())
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.UNAVAILABLE)
                .hasMessageNotContaining(NPSSO);
        assertThat(authenticator.authFailingSince()).isEqualTo(clock.instant());
    }

    @Test
    void successfulSignInClearsTheAuthFailureFlag() {
        server.expect(requestTo(startsWith(AUTHORIZE_URL))).andRespond(withStatus(HttpStatus.FOUND));
        assertThatThrownBy(() -> authenticator.accessToken()).isInstanceOf(PsnIntegrationException.class);
        assertThat(authenticator.authFailingSince()).isNotNull();

        server.reset();
        clock.advance(PsnAuthenticator.SIGN_IN_RETRY_COOLDOWN.plusSeconds(1));
        signIn("access-1", "refresh-1");

        assertThat(authenticator.authFailingSince()).isNull();
    }

    @Test
    void rejectedNpssoIsNotRetriedDuringTheCooldown() {
        server.expect(requestTo(startsWith(AUTHORIZE_URL))).andRespond(withStatus(HttpStatus.FOUND));
        assertThatThrownBy(() -> authenticator.accessToken()).isInstanceOf(PsnIntegrationException.class);
        server.verify();
        server.reset();

        clock.advance(Duration.ofMinutes(1));

        // No request expected: MockRestServiceServer would fail on an unexpected call.
        assertThatThrownBy(() -> authenticator.accessToken())
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.UNAVAILABLE);
        server.verify();
    }

    @Test
    void networkErrorIsUnavailableButNotAnAuthFailure() {
        server.expect(requestTo(startsWith(AUTHORIZE_URL))).andRespond(withException(new SocketTimeoutException("timeout")));

        assertThatThrownBy(() -> authenticator.accessToken())
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.UNAVAILABLE);
        assertThat(authenticator.authFailingSince()).isNull();
    }

    @Test
    void rateLimitedAuthIsReportedAsRateLimited() {
        server.expect(requestTo(startsWith(AUTHORIZE_URL))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> authenticator.accessToken())
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.RATE_LIMITED);
    }

    @Test
    void invalidatedTokenIsRenewedOnTheNextCall() {
        signIn("access-1", "refresh-1");
        server.reset();
        server.expect(requestTo(TOKEN_URL))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andRespond(withSuccess(tokens("access-2", "refresh-2"), MediaType.APPLICATION_JSON));

        authenticator.invalidate("access-1");

        assertThat(authenticator.accessToken()).isEqualTo("access-2");
        server.verify();
    }

    @Test
    void blankNpssoMeansNotConfigured() {
        PsnAuthenticator unconfigured = new PsnAuthenticator(RestClient.create(), "  ", clock);

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(unconfigured::accessToken)
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.NOT_CONFIGURED);
    }

    @Test
    void extractsTheCodeFromTheCustomSchemeRedirect() {
        assertThat(PsnAuthenticator.extractAccessCode(URI.create(CODE_REDIRECT))).isEqualTo("v3.ABC123");
    }

    private void signIn(String accessToken, String refreshToken) {
        expectAuthorize();
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(tokens(accessToken, refreshToken), MediaType.APPLICATION_JSON));
        assertThat(authenticator.accessToken()).isEqualTo(accessToken);
    }

    private void expectAuthorize() {
        server.expect(requestTo(startsWith(AUTHORIZE_URL)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "npsso=" + NPSSO))
                .andExpect(queryParam("client_id", PsnAuthenticator.CLIENT_ID))
                .andExpect(queryParam("response_type", "code"))
                .andExpect(queryParam("access_type", "offline"))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI.create(CODE_REDIRECT)));
    }

    private static String tokens(String accessToken, String refreshToken) {
        return """
                {"access_token":"%s","token_type":"bearer","expires_in":3599,
                 "refresh_token":"%s","refresh_token_expires_in":5183999,"scope":"psn:mobile.v2.core psn:clientapp"}"""
                .formatted(accessToken, refreshToken);
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
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
