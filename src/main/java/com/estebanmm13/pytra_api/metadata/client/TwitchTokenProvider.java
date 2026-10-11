package com.estebanmm13.pytra_api.metadata.client;

import com.estebanmm13.pytra_api.error.IgdbIntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * App access token for IGDB (Twitch OAuth client credentials flow), kept in memory and renewed
 * shortly before it expires, or right away when IGDB rejects it ({@link #invalidate(String)}).
 *
 * <p>Thread-safe: methods are synchronized, so concurrent requests on a cold cache trigger one token
 * request instead of a burst. Tokens last ~60 days, so holding the lock during the rare refresh
 * is cheaper than any cleverness.
 */
@Slf4j
final class TwitchTokenProvider {

    static final String TOKEN_URL = "https://id.twitch.tv/oauth2/token";
    /** Renew this long before the announced expiry, so an in-flight request never carries a dying token. */
    static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private String token;
    private Instant refreshAt = Instant.MIN;

    TwitchTokenProvider(RestClient restClient, String clientId, String clientSecret, Clock clock) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    synchronized String token() {
        if (token != null && clock.instant().isBefore(refreshAt)) return token;
        token = null;
        requestToken();
        return token;
    }

    /** Forgets {@code rejected} (if it is still the current token) so the next call fetches a new one. */
    synchronized void invalidate(String rejected) {
        if (rejected != null && rejected.equals(token)) token = null;
    }

    private void requestToken() {
        // Credentials go in the form body, never in the URL (URLs end up in logs and exception messages).
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "client_credentials");

        String json;
        try {
            json = restClient.post().uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // 400/403 here means the configured client id/secret are wrong or revoked.
            log.warn("Twitch token request answered HTTP {}", e.getStatusCode().value());
            throw IgdbIntegrationException.unavailable();
        } catch (RestClientException e) {
            log.warn("Twitch token request failed: {}", e.getClass().getSimpleName());
            throw IgdbIntegrationException.unavailable();
        }

        JsonNode root;
        try {
            root = json == null ? null : objectMapper.readTree(json);
        } catch (Exception e) {
            root = null;
        }
        String accessToken = root == null ? null : root.path("access_token").asText(null);
        long expiresIn = root == null ? 0 : root.path("expires_in").asLong(0);
        if (accessToken == null || accessToken.isBlank() || expiresIn <= 0) {
            log.warn("Twitch token response had no usable access_token/expires_in");
            throw IgdbIntegrationException.unavailable();
        }

        Duration lifetime = Duration.ofSeconds(expiresIn);
        Duration margin = lifetime.compareTo(REFRESH_MARGIN.multipliedBy(2)) > 0 ? REFRESH_MARGIN : lifetime.dividedBy(2);
        token = accessToken;
        refreshAt = clock.instant().plus(lifetime.minus(margin));
    }
}
