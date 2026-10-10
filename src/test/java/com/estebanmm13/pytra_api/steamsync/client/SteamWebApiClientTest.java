package com.estebanmm13.pytra_api.steamsync.client;

import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SteamWebApiClientTest {

    private static final String API_KEY = "secret-steam-key";
    private static final String STEAM_ID = "76561197960287930";
    private static final String OWNED_GAMES_URL = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/";

    private MockRestServiceServer server;
    private SteamWebApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = SteamWebApiClient.configure(RestClient.builder());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SteamWebApiClient(builder.build(), API_KEY);
    }

    @Test
    void getOwnedGamesParsesLibraryAndAsksForFreeGames() {
        server.expect(requestTo(startsWith(OWNED_GAMES_URL)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("key", API_KEY))
                .andExpect(queryParam("steamid", STEAM_ID))
                .andExpect(queryParam("include_appinfo", "true"))
                .andExpect(queryParam("include_played_free_games", "true"))
                .andRespond(withSuccess("""
                        {"response":{"game_count":2,"games":[
                          {"appid":620,"name":"Portal 2","playtime_forever":600},
                          {"appid":570,"name":"Dota 2","playtime_forever":0}
                        ]}}""", MediaType.APPLICATION_JSON));

        SteamOwnedGamesResult result = client.getOwnedGames(STEAM_ID);

        server.verify();
        assertThat(result.profilePrivate()).isFalse();
        assertThat(result.games()).containsExactly(
                new SteamOwnedGame(620, "Portal 2", 600),
                new SteamOwnedGame(570, "Dota 2", 0));
    }

    @Test
    void parsesLastPlayedAsInstantAndTreatsZeroOrMissingAsNever() {
        server.expect(requestTo(startsWith(OWNED_GAMES_URL)))
                .andRespond(withSuccess("""
                        {"response":{"game_count":3,"games":[
                          {"appid":620,"name":"Portal 2","playtime_forever":600,"rtime_last_played":1700000000},
                          {"appid":570,"name":"Dota 2","playtime_forever":0,"rtime_last_played":0},
                          {"appid":440,"name":"Team Fortress 2","playtime_forever":5}
                        ]}}""", MediaType.APPLICATION_JSON));

        SteamOwnedGamesResult result = client.getOwnedGames(STEAM_ID);

        assertThat(result.games()).containsExactly(
                new SteamOwnedGame(620, "Portal 2", 600, Instant.ofEpochSecond(1_700_000_000L)),
                new SteamOwnedGame(570, "Dota 2", 0, null),
                new SteamOwnedGame(440, "Team Fortress 2", 5, null));
    }

    @Test
    void emptyResponseObjectMeansPrivateProfile() {
        server.expect(requestTo(startsWith(OWNED_GAMES_URL)))
                .andRespond(withSuccess("{\"response\":{}}", MediaType.APPLICATION_JSON));

        SteamOwnedGamesResult result = client.getOwnedGames(STEAM_ID);

        assertThat(result.profilePrivate()).isTrue();
        assertThat(result.games()).isEmpty();
    }

    @Test
    void publicProfileWithNoGamesIsNotPrivate() {
        server.expect(requestTo(startsWith(OWNED_GAMES_URL)))
                .andRespond(withSuccess("{\"response\":{\"game_count\":0}}", MediaType.APPLICATION_JSON));

        SteamOwnedGamesResult result = client.getOwnedGames(STEAM_ID);

        assertThat(result.profilePrivate()).isFalse();
        assertThat(result.games()).isEmpty();
    }

    @ParameterizedTest(name = "HTTP {0} -> {1}")
    @CsvSource({
            "401, STEAM_API_KEY_REJECTED",
            "403, STEAM_API_KEY_REJECTED",
            "429, STEAM_RATE_LIMITED",
            "500, STEAM_UNAVAILABLE",
            "503, STEAM_UNAVAILABLE"
    })
    void mapsHttpErrorsToStableCodes(int httpStatus, String expectedCode) {
        server.expect(requestTo(startsWith(OWNED_GAMES_URL)))
                .andRespond(withStatus(HttpStatus.valueOf(httpStatus)));

        assertThatThrownBy(() -> client.getOwnedGames(STEAM_ID))
                .isInstanceOfSatisfying(SteamIntegrationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(expectedCode);
                    assertThat(e.getMessage()).doesNotContain(API_KEY);
                    assertThat(e.getCause()).isNull();
                });
    }

    @Test
    void timeoutMapsToUnavailableWithoutLeakingTheKey() {
        server.expect(requestTo(startsWith(OWNED_GAMES_URL)))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.getOwnedGames(STEAM_ID))
                .isInstanceOfSatisfying(SteamIntegrationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(SteamIntegrationException.UNAVAILABLE);
                    assertThat(e.getMessage()).doesNotContain(API_KEY);
                    assertThat(e.getCause()).isNull();
                });
    }

    @Test
    void unparseableBodyMapsToUnavailable() {
        server.expect(requestTo(startsWith(OWNED_GAMES_URL)))
                .andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.getOwnedGames(STEAM_ID))
                .isInstanceOfSatisfying(SteamIntegrationException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SteamIntegrationException.UNAVAILABLE));
    }

    @Test
    void blankKeyIsNotConfiguredAndNeverCallsSteam() {
        RestClient.Builder builder = SteamWebApiClient.configure(RestClient.builder());
        MockRestServiceServer noCalls = MockRestServiceServer.bindTo(builder).build();
        SteamWebApiClient unconfigured = new SteamWebApiClient(builder.build(), " ");

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(unconfigured.getPersonaName(STEAM_ID)).isNull();
        assertThatThrownBy(() -> unconfigured.getOwnedGames(STEAM_ID))
                .isInstanceOfSatisfying(SteamIntegrationException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SteamIntegrationException.NOT_CONFIGURED));
        noCalls.verify();
    }

    @Test
    void getPersonaNameReadsFirstPlayer() {
        server.expect(requestTo(startsWith("https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/")))
                .andExpect(queryParam("steamids", STEAM_ID))
                .andRespond(withSuccess("{\"response\":{\"players\":[{\"personaname\":\"Gabe\"}]}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getPersonaName(STEAM_ID)).isEqualTo("Gabe");
    }
}
