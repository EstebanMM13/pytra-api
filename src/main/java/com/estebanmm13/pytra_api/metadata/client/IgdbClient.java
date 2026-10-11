package com.estebanmm13.pytra_api.metadata.client;

import com.estebanmm13.pytra_api.error.IgdbIntegrationException;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.IgdbSearchResultDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Client for the IGDB API (owned by Twitch), used to prefill the game form. Unlike the Steam store
 * it covers console exclusives (Nintendo, PlayStation...).
 *
 * <p>Needs {@code app.igdb.client-id} / {@code app.igdb.client-secret} (TWITCH_CLIENT_ID /
 * TWITCH_CLIENT_SECRET); with either blank the client is {@link #isEnabled() disabled} and never
 * calls out. IGDB allows 4 requests/s: each lookup is a single request (plus a token request every
 * ~60 days) and the service caches results, so no throttling is done here; a 429 is just
 * IGDB_UNAVAILABLE, like every other failure.
 */
@Slf4j
@Component
public class IgdbClient {

    static final String GAMES_URL = "https://api.igdb.com/v4/games";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;
    private final String clientId;
    private final boolean enabled;
    private final TwitchTokenProvider tokenProvider;

    @Autowired
    public IgdbClient(@Value("${app.igdb.client-id:}") String clientId,
                      @Value("${app.igdb.client-secret:}") String clientSecret) {
        this(configure(RestClient.builder()).build(), clientId, clientSecret, Clock.systemUTC());
    }

    IgdbClient(RestClient restClient, String clientId, String clientSecret, Clock clock) {
        this.restClient = restClient;
        this.clientId = clientId == null ? "" : clientId.strip();
        String secret = clientSecret == null ? "" : clientSecret.strip();
        this.enabled = !this.clientId.isEmpty() && !secret.isEmpty();
        this.tokenProvider = new TwitchTokenProvider(restClient, this.clientId, secret, clock);
        if (!enabled) log.info("TWITCH_CLIENT_ID/TWITCH_CLIENT_SECRET not set: IGDB autofill is disabled");
    }

    /**
     * Same timeouts as the Steam store client (3s connect / 5s read): a user is waiting on the form.
     * Uses the JDK HttpClient rather than HttpURLConnection, which turns a 401 on a streamed POST into
     * an I/O error (HttpRetryException) and would hide the "token rejected, renew it" signal.
     */
    static RestClient.Builder configure(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return builder.requestFactory(requestFactory);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** @param limit maximum results returned; a few extra rows are requested to make up for filtered bundles/packs */
    public List<IgdbSearchResultDto> search(String term, int limit) {
        return IgdbParser.parseSearch(query(IgdbQueries.search(term, limit * 2)), limit);
    }

    public IgdbGameMetadataDto getDetails(long igdbId) {
        return IgdbParser.parseDetails(query(IgdbQueries.details(igdbId)), igdbId);
    }

    private JsonNode query(String body) {
        if (!enabled) throw IgdbIntegrationException.disabled();
        String token = tokenProvider.token();
        try {
            return post(body, token);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) throw unavailable(e);
            // Token revoked or expired early: get a fresh one and try exactly once more.
            tokenProvider.invalidate(token);
            try {
                return post(body, tokenProvider.token());
            } catch (RestClientResponseException retryError) {
                throw unavailable(retryError);
            }
        }
    }

    private JsonNode post(String body, String token) {
        String json;
        try {
            json = restClient.post().uri(GAMES_URL)
                    .header("Client-ID", clientId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.TEXT_PLAIN)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("IGDB call failed: {}", e.getClass().getSimpleName());
            throw IgdbIntegrationException.unavailable();
        }
        if (json == null || json.isBlank()) {
            log.warn("IGDB call returned an empty body");
            throw IgdbIntegrationException.unavailable();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("IGDB call returned an unparseable body");
            throw IgdbIntegrationException.unavailable();
        }
    }

    private static IgdbIntegrationException unavailable(RestClientResponseException e) {
        log.warn("IGDB call answered HTTP {}", e.getStatusCode().value());
        return IgdbIntegrationException.unavailable();
    }
}
