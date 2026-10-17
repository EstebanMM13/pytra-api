package com.estebanmm13.pytra_api.psnsync.client;

import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Keeps an access token for Pytra's PSN service account, the same way the PlayStation App does
 * (and the open-source psn-api / psnawp libraries):
 * <ol>
 *   <li>NPSSO cookie → {@code GET /authorize} → 302 whose {@code Location} carries {@code ?code=}</li>
 *   <li>code → {@code POST /token} (grant {@code authorization_code}) → access token (~1 h) + refresh token (~2 months)</li>
 *   <li>access token expired → {@code POST /token} (grant {@code refresh_token}); if Sony rejects the refresh
 *       token, sign in again from the NPSSO</li>
 * </ol>
 * Tokens live in memory only (single API instance): a restart simply signs in again from the NPSSO.
 * All token state is guarded by this object's monitor.
 *
 * <p>The NPSSO and the tokens are never logged nor put in exception messages. When Sony rejects the
 * NPSSO itself (expired after ~2 months), {@link #authFailingSince()} becomes non-null and a WARN is
 * logged so the admin knows to renew {@code PSN_NPSSO}; users only get {@code PSN_UNAVAILABLE}.
 */
@Slf4j
@Component
public class PsnAuthenticator {

    static final String AUTH_BASE_URL = "https://ca.account.sony.com/api/authz/v3/oauth";
    static final String AUTHORIZE_PATH = "/authorize";
    static final String TOKEN_PATH = "/token";

    // Public client registration of the PlayStation App (same values as psn-api and psnawp); not a secret of Pytra.
    static final String CLIENT_ID = "09515159-7237-4370-9b40-3806e67c0891";
    static final String REDIRECT_URI = "com.scee.psxandroid.scecompcall://redirect";
    static final String SCOPE = "psn:mobile.v2.core psn:clientapp";
    static final String CLIENT_BASIC_AUTH = "Basic MDk1MTUxNTktNzIzNy00MzcwLTliNDAtMzgwNmU2N2MwODkxOnVjUGprYTV0bnRCMktxc1A=";

    /** Tokens are renewed this long before Sony's expiry, so a request never leaves with a dying token. */
    static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    /**
     * After Sony rejected the NPSSO, requests fail fast for this long instead of signing in again on every
     * click: an expired NPSSO stays expired, and hammering the sign-in endpoint risks a lock on the account.
     */
    static final Duration SIGN_IN_RETRY_COOLDOWN = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;
    private final String npsso;
    private final Clock clock;

    private String accessToken;
    private Instant accessTokenExpiresAt;
    private String refreshToken;
    private Instant refreshTokenExpiresAt;
    private volatile Instant authFailingSince;
    private Instant lastRejectedSignInAt;

    @Autowired
    public PsnAuthenticator(@Value("${psn.npsso:}") String npsso) {
        this(configure(RestClient.builder()).build(), npsso, Clock.systemUTC());
    }

    PsnAuthenticator(RestClient restClient, String npsso, Clock clock) {
        this.restClient = restClient;
        this.npsso = npsso == null ? "" : npsso.strip();
        this.clock = clock;
    }

    /**
     * Base URL and timeouts (5s connect / 15s read). Redirects are never followed: the authorize step
     * answers with a 302 whose Location header IS the result.
     */
    static RestClient.Builder configure(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return builder.baseUrl(AUTH_BASE_URL).requestFactory(requestFactory);
    }

    /** False when PSN_NPSSO is blank: the PSN integration is disabled. */
    public boolean isConfigured() {
        return !npsso.isEmpty();
    }

    /** Looks like a real NPSSO (64 characters); only used for a startup warning. */
    public boolean hasExpectedNpssoFormat() {
        return npsso.length() == 64;
    }

    /** When Sony started rejecting the service account (NPSSO expired or revoked); null while healthy. */
    public Instant authFailingSince() {
        return authFailingSince;
    }

    /**
     * A valid access token, renewing it when needed (refresh token first, NPSSO as fallback).
     *
     * @throws PsnIntegrationException PSN_NOT_CONFIGURED without NPSSO; PSN_UNAVAILABLE when Sony is down
     *                                 or rejects the service account; PSN_RATE_LIMITED on HTTP 429
     */
    public synchronized String accessToken() {
        if (!isConfigured()) throw PsnIntegrationException.notConfigured();

        Instant now = clock.instant();
        if (accessToken != null && now.isBefore(accessTokenExpiresAt.minus(EXPIRY_MARGIN))) {
            return accessToken;
        }

        if (refreshToken != null && now.isBefore(refreshTokenExpiresAt.minus(EXPIRY_MARGIN))) {
            try {
                storeTokens(requestTokens(refreshTokenForm(refreshToken), "refresh"));
                return accessToken;
            } catch (CredentialsRejected e) {
                log.info("PSN refresh token rejected; signing in again with the NPSSO");
                clearTokens();
            }
        }

        if (lastRejectedSignInAt != null && now.isBefore(lastRejectedSignInAt.plus(SIGN_IN_RETRY_COOLDOWN))) {
            throw PsnIntegrationException.unavailable();
        }
        try {
            String code = exchangeNpssoForAccessCode();
            storeTokens(requestTokens(authorizationCodeForm(code), "code exchange"));
        } catch (CredentialsRejected e) {
            lastRejectedSignInAt = clock.instant();
            reportServiceAuthFailure(e.getMessage());
            throw PsnIntegrationException.unavailable();
        }
        lastRejectedSignInAt = null;
        return accessToken;
    }

    /** Forgets {@code rejectedToken} (if it is still the current one) so the next call renews it. */
    public synchronized void invalidate(String rejectedToken) {
        if (rejectedToken != null && rejectedToken.equals(accessToken)) {
            accessToken = null;
        }
    }

    /** Sony refused a freshly issued access token: the service account itself is not usable. */
    public synchronized void reportRejectedAccessToken() {
        clearTokens();
        reportServiceAuthFailure("API rejected a fresh access token");
    }

    // ---- Steps ------------------------------------------------------------------------------

    private String exchangeNpssoForAccessCode() {
        ResponseEntity<Void> response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(AUTHORIZE_PATH)
                            .queryParam("access_type", "offline")
                            .queryParam("client_id", CLIENT_ID)
                            .queryParam("redirect_uri", REDIRECT_URI)
                            .queryParam("response_type", "code")
                            .queryParam("scope", SCOPE)
                            .build())
                    .header(HttpHeaders.COOKIE, "npsso=" + npsso)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw translate(e.getStatusCode().value(), "authorize");
        } catch (RestClientException e) {
            log.warn("PSN authorize call failed: {}", e.getClass().getSimpleName());
            throw PsnIntegrationException.unavailable();
        }
        return extractAccessCode(response.getHeaders().getLocation());
    }

    /**
     * The code travels in the redirect's query string; an expired/invalid NPSSO yields a redirect with
     * {@code error} / {@code error_code} (4165) instead, or no redirect at all.
     */
    static String extractAccessCode(URI location) {
        if (location == null) {
            throw new CredentialsRejected("authorize answered without a redirect");
        }
        var query = UriComponentsBuilder.fromUri(location).build().getQueryParams();
        String code = query.getFirst("code");
        if (code == null || code.isBlank()) {
            String errorCode = query.getFirst("error_code");
            throw new CredentialsRejected("authorize redirect carried no code"
                    + (errorCode != null ? " (error_code " + errorCode + ")" : ""));
        }
        return code;
    }

    private JsonNode requestTokens(MultiValueMap<String, String> form, String step) {
        String json;
        try {
            json = restClient.post()
                    .uri(TOKEN_PATH)
                    .header(HttpHeaders.AUTHORIZATION, CLIENT_BASIC_AUTH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw translate(e.getStatusCode().value(), "token " + step);
        } catch (RestClientException e) {
            log.warn("PSN token {} failed: {}", step, e.getClass().getSimpleName());
            throw PsnIntegrationException.unavailable();
        }

        JsonNode tokens = parse(json, step);
        if (tokens.path("access_token").asText("").isEmpty()) {
            throw new CredentialsRejected("token " + step + " returned no access token");
        }
        return tokens;
    }

    private static MultiValueMap<String, String> authorizationCodeForm(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("redirect_uri", REDIRECT_URI);
        form.add("grant_type", "authorization_code");
        form.add("token_format", "jwt");
        return form;
    }

    private static MultiValueMap<String, String> refreshTokenForm(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        form.add("token_format", "jwt");
        form.add("scope", SCOPE);
        return form;
    }

    // ---- State ------------------------------------------------------------------------------

    private void storeTokens(JsonNode tokens) {
        Instant now = clock.instant();
        accessToken = tokens.path("access_token").asText();
        accessTokenExpiresAt = now.plusSeconds(tokens.path("expires_in").asLong(0));
        // A refresh response may omit a new refresh token: keep the current one then.
        String newRefreshToken = tokens.path("refresh_token").asText("");
        if (!newRefreshToken.isEmpty()) {
            refreshToken = newRefreshToken;
            refreshTokenExpiresAt = now.plusSeconds(tokens.path("refresh_token_expires_in").asLong(0));
        }
        if (authFailingSince != null) {
            log.info("PSN service account authenticated again");
            authFailingSince = null;
        }
    }

    private void clearTokens() {
        accessToken = null;
        accessTokenExpiresAt = null;
        refreshToken = null;
        refreshTokenExpiresAt = null;
    }

    private void reportServiceAuthFailure(String reason) {
        if (authFailingSince == null) {
            authFailingSince = clock.instant();
        }
        // No token or NPSSO in the message: only what step failed.
        log.warn("PSN service account authentication failed ({}): the NPSSO is probably expired. "
                + "Renew PSN_NPSSO (see README, PlayStation Network section).", reason);
    }

    // ---- Errors -----------------------------------------------------------------------------

    /**
     * 400/401/403 on an auth step mean Sony rejected the credentials (NPSSO, code or refresh token);
     * anything else is a transient upstream problem.
     */
    private RuntimeException translate(int status, String step) {
        if (status == HttpStatus.BAD_REQUEST.value() || status == HttpStatus.UNAUTHORIZED.value()
                || status == HttpStatus.FORBIDDEN.value()) {
            return new CredentialsRejected(step + " answered HTTP " + status);
        }
        log.warn("PSN {} answered HTTP {}", step, status);
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return PsnIntegrationException.rateLimited();
        }
        return PsnIntegrationException.unavailable();
    }

    private JsonNode parse(String json, String step) {
        if (json == null || json.isBlank()) {
            log.warn("PSN token {} returned an empty body", step);
            throw PsnIntegrationException.unavailable();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("PSN token {} returned an unparseable body", step);
            throw PsnIntegrationException.unavailable();
        }
    }

    /** Sony refused the credentials of one step. Internal: callers turn it into a fallback or PSN_UNAVAILABLE. */
    static final class CredentialsRejected extends RuntimeException {
        CredentialsRejected(String reason) {
            super(reason, null, false, false);
        }
    }
}
