package com.estebanmm13.pytra_api.metadata.client;

import com.estebanmm13.pytra_api.error.IgdbIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IgdbClientTest {

    private static final String TOKEN_URL = "https://id.twitch.tv/oauth2/token";
    private static final String GAMES_URL = "https://api.igdb.com/v4/games";
    private static final String GAME_JSON = "[{\"id\":7,\"name\":\"Knack\"}]";

    private final MutableClock clock = new MutableClock();
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private IgdbClient client;

    @BeforeEach
    void setUp() {
        builder = IgdbClient.configure(RestClient.builder());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IgdbClient(builder.build(), "my-client", "my-secret", clock);
    }

    @Test
    void blankCredentialsDisableTheClientWithoutCallingOut() {
        IgdbClient disabled = new IgdbClient(builder.build(), "my-client", "  ", clock);
        server.expect(never(), requestTo(TOKEN_URL));

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(new IgdbClient(builder.build(), "", "secret", clock).isEnabled()).isFalse();
        assertThat(client.isEnabled()).isTrue();
        assertThatThrownBy(() -> disabled.search("zelda", 10))
                .isInstanceOfSatisfying(IgdbIntegrationException.class,
                        e -> assertThat(e.getCode()).isEqualTo(IgdbIntegrationException.DISABLED));
        server.verify();
    }

    @Test
    void fetchesTheTokenOnceAndSendsItWithTheClientId() {
        expectToken("token-1", 5_000_000);
        expectGames("token-1").andRespond(withSuccess(GAME_JSON, MediaType.APPLICATION_JSON));
        expectGames("token-1").andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.getDetails(7).name()).isEqualTo("Knack");
        assertThat(client.search("knack", 10)).isEmpty();

        server.verify();
    }

    @Test
    void tokenRequestCarriesTheCredentialsInTheBodyNotTheUrl() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(containsString("client_id=my-client"),
                        containsString("client_secret=my-secret"), containsString("grant_type=client_credentials"))))
                .andRespond(withSuccess("{\"access_token\":\"t\",\"expires_in\":5000000}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(GAMES_URL))
                .andExpect(content().string(allOf(containsString("search \"knack\""), not(containsString("my-secret")))))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.search("knack", 10);

        server.verify();
    }

    @Test
    void renewsTheTokenBeforeItExpires() {
        expectToken("token-1", 3600);
        expectGames("token-1").andRespond(withSuccess(GAME_JSON, MediaType.APPLICATION_JSON));
        expectToken("token-2", 3600);
        expectGames("token-2").andRespond(withSuccess(GAME_JSON, MediaType.APPLICATION_JSON));

        client.getDetails(7);
        clock.advance(Duration.ofMinutes(56)); // inside the 5-minute refresh margin
        client.getDetails(7);

        server.verify();
    }

    @Test
    void a401RenewsTheTokenAndRetriesOnce() {
        expectToken("stale", 5_000_000);
        expectGames("stale").andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken("fresh", 5_000_000);
        expectGames("fresh").andRespond(withSuccess(GAME_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.getDetails(7).name()).isEqualTo("Knack");
        server.verify();
    }

    @Test
    void aSecond401IsUnavailable() {
        expectToken("stale", 5_000_000);
        expectGames("stale").andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken("also-rejected", 5_000_000);
        expectGames("also-rejected").andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertUnavailable(() -> client.getDetails(7));
        server.verify();
    }

    @ParameterizedTest(name = "IGDB HTTP {0} -> IGDB_UNAVAILABLE")
    @ValueSource(ints = {400, 429, 500, 503})
    void igdbHttpErrorsBecomeUnavailable(int httpStatus) {
        expectToken("token", 5_000_000);
        expectGames("token").andRespond(withStatus(HttpStatus.valueOf(httpStatus)));

        assertUnavailable(() -> client.search("zelda", 10));
    }

    @ParameterizedTest(name = "token HTTP {0} -> IGDB_UNAVAILABLE")
    @ValueSource(ints = {400, 403, 500})
    void rejectedCredentialsBecomeUnavailable(int httpStatus) {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.valueOf(httpStatus)));

        assertUnavailable(() -> client.search("zelda", 10));
    }

    @Test
    void tokenWithoutAccessTokenIsUnavailable() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess("{\"message\":\"nope\"}", MediaType.APPLICATION_JSON));

        assertUnavailable(() -> client.search("zelda", 10));
    }

    @Test
    void timeoutAndGarbageBecomeUnavailable() {
        expectToken("token", 5_000_000);
        expectGames("token").andRespond(withException(new SocketTimeoutException("Read timed out")));
        expectGames("token").andRespond(withSuccess("<html>busy</html>", MediaType.TEXT_HTML));

        assertUnavailable(() -> client.search("zelda", 10));
        assertUnavailable(() -> client.search("zelda", 10));
    }

    private void expectToken(String token, long expiresIn) {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"%s\",\"expires_in\":%d,\"token_type\":\"bearer\"}"
                        .formatted(token, expiresIn), MediaType.APPLICATION_JSON));
    }

    private ResponseActions expectGames(String token) {
        return server.expect(requestTo(GAMES_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Client-ID", "my-client"))
                .andExpect(header("Authorization", "Bearer " + token));
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(IgdbIntegrationException.class, e -> {
            assertThat(e.getCode()).isEqualTo(IgdbIntegrationException.UNAVAILABLE);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        });
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
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
