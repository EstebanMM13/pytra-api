package com.estebanmm13.pytra_api.steamsync.client;

import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin client for the Steam Web API.
 *
 * <p>The API key travels in the query string, so exception messages and request URIs from the
 * HTTP layer may contain it. They are never logged or propagated: every failure is translated
 * into a {@link SteamIntegrationException} with a stable code and no cause attached.
 */
@Slf4j
@Component
public class SteamWebApiClient {

    static final String BASE_URL = "https://api.steampowered.com";
    static final String OWNED_GAMES_PATH = "/IPlayerService/GetOwnedGames/v1/";
    static final String PLAYER_SUMMARIES_PATH = "/ISteamUser/GetPlayerSummaries/v2/";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public SteamWebApiClient(@Value("${steam.api-key:}") String apiKey) {
        this(configure(RestClient.builder()).build(), apiKey);
    }

    SteamWebApiClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    /** Base URL and timeouts (5s connect / 15s read): a hung Steam must never pin a request thread. */
    static RestClient.Builder configure(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return builder.baseUrl(BASE_URL).requestFactory(requestFactory);
    }

    /** False when STEAM_API_KEY is blank: linking still works (OpenID needs no key), syncing does not. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Best-effort display name; null when not configured or not available. */
    public String getPersonaName(String steamId64) {
        if (!isConfigured()) return null;
        JsonNode players = get(PLAYER_SUMMARIES_PATH, "steamids", steamId64).path("response").path("players");
        if (!players.isArray() || players.isEmpty()) return null;
        return players.get(0).path("personaname").asText(null);
    }

    public SteamOwnedGamesResult getOwnedGames(String steamId64) {
        if (!isConfigured()) throw SteamIntegrationException.notConfigured();

        JsonNode response = get(OWNED_GAMES_PATH, "steamid", steamId64).path("response");
        // A private profile (or private "game details") answers 200 with an empty response object.
        if (!response.has("game_count")) {
            return SteamOwnedGamesResult.privateProfile();
        }

        JsonNode games = response.path("games");
        List<SteamOwnedGame> result = new ArrayList<>();
        if (games.isArray()) {
            for (JsonNode game : games) {
                result.add(new SteamOwnedGame(
                        game.path("appid").asLong(),
                        game.path("name").asText("Unknown"),
                        game.path("playtime_forever").asLong(0)
                ));
            }
        }
        return new SteamOwnedGamesResult(false, result);
    }

    private JsonNode get(String path, String idParam, String steamId64) {
        String json;
        try {
            json = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path)
                                .queryParam("key", apiKey)
                                .queryParam(idParam, steamId64)
                                .queryParam("format", "json");
                        if (OWNED_GAMES_PATH.equals(path)) {
                            uriBuilder.queryParam("include_appinfo", "true")
                                    .queryParam("include_played_free_games", "true");
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw translate(HttpStatus.resolve(e.getStatusCode().value()), path);
        } catch (RestClientException e) {
            // Timeouts / connection errors. The message embeds the request URI (and the key): do not log it.
            log.warn("Steam Web API call {} failed: {}", path, e.getClass().getSimpleName());
            throw SteamIntegrationException.unavailable();
        }
        return parse(json, path);
    }

    private SteamIntegrationException translate(HttpStatus status, String path) {
        log.warn("Steam Web API call {} answered HTTP {}", path, status);
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            return SteamIntegrationException.apiKeyRejected();
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return SteamIntegrationException.rateLimited();
        }
        return SteamIntegrationException.unavailable();
    }

    private JsonNode parse(String json, String path) {
        if (json == null || json.isBlank()) {
            log.warn("Steam Web API call {} returned an empty body", path);
            throw SteamIntegrationException.unavailable();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Steam Web API call {} returned an unparseable body", path);
            throw SteamIntegrationException.unavailable();
        }
    }
}
