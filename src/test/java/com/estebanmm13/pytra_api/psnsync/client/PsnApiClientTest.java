package com.estebanmm13.pytra_api.psnsync.client;

import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PsnApiClientTest {

    private static final String ACCOUNT_ID = "962157895908076652";
    private static final String PROFILE_URL =
            "https://us-prof.np.community.playstation.net/userProfile/v1/users/EstebanK13/profile2";
    private static final String TITLES_URL = "https://m.np.playstation.com/api/gamelist/v2/users/" + ACCOUNT_ID + "/titles";

    private final PsnAuthenticator authenticator = mock(PsnAuthenticator.class);
    private MockRestServiceServer server;
    private PsnApiClient client;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-17T12:00:00Z"));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = PsnApiClient.configure(RestClient.builder());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PsnApiClient(builder.build(), authenticator, clock);
        when(authenticator.isConfigured()).thenReturn(true);
        when(authenticator.accessToken()).thenReturn("token-1");
    }

    // ---- Online ID -> account ------------------------------------------------------------

    @Test
    void resolvesOnlineIdToAccountWithTheServiceAccountToken() {
        server.expect(requestTo(startsWith(PROFILE_URL)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andExpect(queryParam("fields", "accountId,onlineId,currentOnlineId"))
                .andRespond(withSuccess("""
                        {"profile":{"onlineId":"EstebanK13","accountId":"%s"}}""".formatted(ACCOUNT_ID),
                        MediaType.APPLICATION_JSON));

        PsnAccount account = client.findAccount("EstebanK13");

        server.verify();
        assertThat(account).isEqualTo(new PsnAccount(ACCOUNT_ID, "EstebanK13"));
    }

    @Test
    void prefersTheCurrentOnlineIdAfterARename() {
        server.expect(requestTo(startsWith(PROFILE_URL)))
                .andRespond(withSuccess("""
                        {"profile":{"onlineId":"EstebanK13","currentOnlineId":"NewName","accountId":"%s"}}"""
                        .formatted(ACCOUNT_ID), MediaType.APPLICATION_JSON));

        assertThat(client.findAccount("EstebanK13").onlineId()).isEqualTo("NewName");
    }

    @Test
    void unknownOnlineIdIsUserNotFound() {
        server.expect(requestTo(startsWith(PROFILE_URL)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"referenceId":"x","code":2105356,"message":"User not found"}}"""));

        assertThatThrownBy(() -> client.findAccount("EstebanK13"))
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.USER_NOT_FOUND);
    }

    @Test
    void notFoundErrorInsideA200IsUserNotFound() {
        server.expect(requestTo(startsWith(PROFILE_URL)))
                .andRespond(withSuccess("""
                        {"error":{"code":2105356,"message":"User not found"}}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findAccount("EstebanK13"))
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.USER_NOT_FOUND);
    }

    @Test
    void profileHiddenByPrivacyIsProfilePrivate() {
        server.expect(requestTo(startsWith(PROFILE_URL)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"code":2240526,"message":"Not permitted by access control"}}"""));

        assertThatThrownBy(() -> client.findAccount("EstebanK13"))
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.PROFILE_PRIVATE);
    }

    // ---- Game list -------------------------------------------------------------------------

    @Test
    void parsesPlayedTitlesAndFollowsPagination() {
        server.expect(requestTo(startsWith(TITLES_URL)))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andExpect(queryParam("categories", "ps4_game,ps5_native_game"))
                .andExpect(queryParam("limit", "200"))
                .andExpect(queryParam("offset", "0"))
                .andRespond(withSuccess("""
                        {"titles":[
                          {"titleId":"PPSA01411_00","name":"Demon's Souls","imageUrl":"https://image.api.playstation.com/ds.png",
                           "category":"ps5_native_game","playCount":12,"playDuration":"PT40H29M31S",
                           "firstPlayedDateTime":"2020-11-12T19:40:19Z","lastPlayedDateTime":"2024-08-03T19:28:27.12Z",
                           "concept":{"id":10000177,"titleIds":["PPSA01411_00","PPSA01412_00"],"name":"Demon's Souls"}}
                        ],"nextOffset":1,"previousOffset":0,"totalItemCount":2}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(TITLES_URL)))
                .andExpect(queryParam("offset", "1"))
                .andRespond(withSuccess("""
                        {"titles":[
                          {"titleId":"CUSA00900_00","name":"Bloodborne","category":"ps4_game","playCount":3,
                           "playDuration":"PT1H"}
                        ],"previousOffset":0,"totalItemCount":2}""", MediaType.APPLICATION_JSON));

        PsnPlayedGamesResult result = client.getPlayedGames(ACCOUNT_ID);

        server.verify();
        assertThat(result.profilePrivate()).isFalse();
        assertThat(result.titles()).containsExactly(
                new PsnPlayedTitle("PPSA01411_00", "Demon's Souls", "https://image.api.playstation.com/ds.png",
                        ExternalPlatform.PSN_PS5, 12, 40 * 60 + 30,
                        Instant.parse("2020-11-12T19:40:19Z"), Instant.parse("2024-08-03T19:28:27.12Z"),
                        10000177L),
                new PsnPlayedTitle("CUSA00900_00", "Bloodborne", null, ExternalPlatform.PSN_PS4, 3, 60, null, null));
    }

    @Test
    void conceptIdIsReadAsAPositiveNumberOrLeftUnknown() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertThat(PsnApiClient.conceptId(mapper.readTree("10000177"))).isEqualTo(10000177L);
        assertThat(PsnApiClient.conceptId(mapper.readTree("\"234567\""))).isEqualTo(234567L);
        assertThat(PsnApiClient.conceptId(mapper.readTree("0"))).isNull();
        assertThat(PsnApiClient.conceptId(mapper.readTree("-5"))).isNull();
        assertThat(PsnApiClient.conceptId(mapper.readTree("\"abc\""))).isNull();
        assertThat(PsnApiClient.conceptId(mapper.readTree("{}").path("id"))).isNull();
        assertThat(PsnApiClient.conceptId(mapper.readTree("null"))).isNull();
    }

    @Test
    void skipsTitlesThatAreNotPs4OrPs5Games() {
        server.expect(requestTo(startsWith(TITLES_URL)))
                .andRespond(withSuccess("""
                        {"titles":[
                          {"titleId":"PPSA1_00","name":"PC thing","category":"pspc_game","playDuration":"PT1H"},
                          {"titleId":"X_00","name":"Mystery","category":"unknown","playDuration":"PT1H"}
                        ],"totalItemCount":2}""", MediaType.APPLICATION_JSON));

        assertThat(client.getPlayedGames(ACCOUNT_ID).titles()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "403, {\"error\":{\"code\":2240526\\,\"message\":\"Not permitted by access control\"}}",
            "403, ''"
    })
    void hiddenGameListIsReportedAsPrivateProfile(int status, String body) {
        server.expect(requestTo(startsWith(TITLES_URL)))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(body));

        assertThat(client.getPlayedGames(ACCOUNT_ID).profilePrivate()).isTrue();
    }

    @Test
    void accessDeniedErrorInsideA200IsPrivateProfile() {
        server.expect(requestTo(startsWith(TITLES_URL)))
                .andRespond(withSuccess("""
                        {"error":{"code":2240526,"message":"Not permitted by access control"}}""", MediaType.APPLICATION_JSON));

        assertThat(client.getPlayedGames(ACCOUNT_ID).profilePrivate()).isTrue();
    }

    @Test
    void expiredTokenIsRenewedAndTheCallRetriedOnce() {
        when(authenticator.accessToken()).thenReturn("token-1", "token-2");
        server.expect(requestTo(startsWith(TITLES_URL)))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(startsWith(TITLES_URL)))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-2"))
                .andRespond(withSuccess("{\"titles\":[],\"totalItemCount\":0}", MediaType.APPLICATION_JSON));

        assertThat(client.getPlayedGames(ACCOUNT_ID).titles()).isEmpty();
        server.verify();
        verify(authenticator).invalidate("token-1");
    }

    @Test
    void secondUnauthorizedFlagsTheServiceAccountAndIsUnavailable() {
        server.expect(requestTo(startsWith(TITLES_URL))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(startsWith(TITLES_URL))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getPlayedGames(ACCOUNT_ID))
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.UNAVAILABLE);
        verify(authenticator).reportRejectedAccessToken();
    }

    @ParameterizedTest
    @CsvSource({
            "429, PSN_RATE_LIMITED",
            "500, PSN_UNAVAILABLE",
            "503, PSN_UNAVAILABLE",
            "404, PSN_USER_NOT_FOUND"
    })
    void mapsGameListErrorStatuses(int status, String expectedCode) {
        server.expect(requestTo(startsWith(TITLES_URL))).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> client.getPlayedGames(ACCOUNT_ID))
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(expectedCode);
    }

    @Test
    void rateLimitHonorsRetryAfterAndFailsFastMeanwhile() {
        server.expect(requestTo(startsWith(TITLES_URL)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "120"));

        assertThatThrownBy(() -> client.getPlayedGames(ACCOUNT_ID)).hasMessage(PsnIntegrationException.RATE_LIMITED);
        server.verify();
        server.reset();
        assertThat(client.isRateLimited()).isTrue();

        // Within the window no request leaves, whatever the call (a lookup at link time too).
        clock.advance(Duration.ofSeconds(119));
        assertThatThrownBy(() -> client.findAccount("EstebanK13")).hasMessage(PsnIntegrationException.RATE_LIMITED);
        server.verify();

        clock.advance(Duration.ofSeconds(2));
        assertThat(client.isRateLimited()).isFalse();
        server.expect(requestTo(startsWith(TITLES_URL)))
                .andRespond(withSuccess("{\"titles\":[],\"totalItemCount\":0}", MediaType.APPLICATION_JSON));
        assertThat(client.getPlayedGames(ACCOUNT_ID).titles()).isEmpty();
        server.verify();
    }

    @Test
    void rateLimitWithoutRetryAfterPausesFifteenMinutes() {
        server.expect(requestTo(startsWith(TITLES_URL))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.getPlayedGames(ACCOUNT_ID)).hasMessage(PsnIntegrationException.RATE_LIMITED);

        clock.advance(Duration.ofMinutes(14));
        assertThat(client.isRateLimited()).isTrue();
        clock.advance(Duration.ofMinutes(2));
        assertThat(client.isRateLimited()).isFalse();
    }

    @Test
    void rateLimitedSignInStartsTheSameCooldown() {
        when(authenticator.accessToken()).thenThrow(PsnIntegrationException.rateLimited());

        assertThatThrownBy(() -> client.getPlayedGames(ACCOUNT_ID)).hasMessage(PsnIntegrationException.RATE_LIMITED);

        assertThat(client.isRateLimited()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "300, 300",
            "5, 60",                                   // clamped to 1 min
            "86400, 3600",                             // clamped to 1 h
            "'Sat, 17 Oct 2026 12:10:00 GMT', 600",    // HTTP date
            "garbage, 900",
            "'', 900"
    })
    void parsesRetryAfter(String header, long expectedSeconds) {
        assertThat(PsnApiClient.retryAfter(header, Instant.parse("2026-10-17T12:00:00Z")).toSeconds())
                .isEqualTo(expectedSeconds);
    }

    @Test
    void timeoutIsUnavailable() {
        server.expect(requestTo(startsWith(TITLES_URL))).andRespond(withException(new SocketTimeoutException("boom")));

        assertThatThrownBy(() -> client.getPlayedGames(ACCOUNT_ID))
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.UNAVAILABLE);
    }

    @Test
    void notConfiguredFailsWithoutCallingPsn() {
        when(authenticator.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> client.getPlayedGames(ACCOUNT_ID))
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.NOT_CONFIGURED);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "ps5_native_game, PSN_PS5",
            "ps4_game, PSN_PS4"
    })
    void mapsCategoriesToLinkPlatforms(String category, ExternalPlatform expected) {
        assertThat(PsnApiClient.platformOf(category)).isEqualTo(expected);
    }

    @Test
    void otherCategoriesHaveNoPlatform() {
        assertThat(PsnApiClient.platformOf("pspc_game")).isNull();
        assertThat(PsnApiClient.platformOf("unknown")).isNull();
        assertThat(PsnApiClient.platformOf(null)).isNull();
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
