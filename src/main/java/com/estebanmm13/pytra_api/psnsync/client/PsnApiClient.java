package com.estebanmm13.pytra_api.psnsync.client;

import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin client for the (unofficial) PlayStation Network API, called with Pytra's service account.
 * Endpoints are the ones the PlayStation App uses, as documented by psn-api and psnawp.
 *
 * <p>Every failure is translated into a {@link PsnIntegrationException} with a stable code and no
 * cause attached: HTTP-layer messages may carry the bearer token.
 */
@Slf4j
@Component
public class PsnApiClient {

    /** Legacy profile endpoint: the one psnawp uses to resolve an online ID to an account id. */
    static final String PROFILE_URL = "https://us-prof.np.community.playstation.net/userProfile/v1/users/{onlineId}/profile2"
            + "?fields=accountId,onlineId,currentOnlineId";
    /** Played games ("gamelist") with playtime; PS4 and PS5 only (older consoles are not reported). */
    static final String TITLES_URL = "https://m.np.playstation.com/api/gamelist/v2/users/{accountId}/titles"
            + "?categories=ps4_game,ps5_native_game&limit={limit}&offset={offset}";

    static final int PAGE_SIZE = 200;
    /** Safety net against a pagination loop: 50 pages = 10,000 titles. */
    static final int MAX_PAGES = 50;

    static final String PS4_CATEGORY = "ps4_game";
    static final String PS5_CATEGORY = "ps5_native_game";

    /** Pause after a 429 without a usable Retry-After (PSN's budget is ~300 requests per 15 min). */
    static final Duration DEFAULT_RATE_LIMIT_COOLDOWN = Duration.ofMinutes(15);
    static final Duration MIN_RATE_LIMIT_COOLDOWN = Duration.ofMinutes(1);
    static final Duration MAX_RATE_LIMIT_COOLDOWN = Duration.ofHours(1);

    /** Sony error code for an unknown online ID on the profile endpoint. */
    static final int USER_NOT_FOUND_ERROR = 2105356;
    /** Sony error code for "Not permitted by access control" (privacy settings hide the data). */
    static final int ACCESS_DENIED_ERROR = 2240526;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;
    private final PsnAuthenticator authenticator;
    private final Clock clock;

    /**
     * Every user shares one service account, so a 429 from Sony applies to all of them: until this instant
     * no call leaves (link, manual sync, scheduler all fail fast with PSN_RATE_LIMITED). Null when not limited.
     */
    private volatile Instant rateLimitedUntil;

    @Autowired
    public PsnApiClient(PsnAuthenticator authenticator) {
        this(configure(RestClient.builder()).build(), authenticator, Clock.systemUTC());
    }

    PsnApiClient(RestClient restClient, PsnAuthenticator authenticator) {
        this(restClient, authenticator, Clock.systemUTC());
    }

    PsnApiClient(RestClient restClient, PsnAuthenticator authenticator, Clock clock) {
        this.restClient = restClient;
        this.authenticator = authenticator;
        this.clock = clock;
    }

    /** Timeouts (5s connect / 15s read): a hung PSN must never pin a request thread. No base URL: two hosts. */
    static RestClient.Builder configure(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return builder.requestFactory(requestFactory)
                // English names, like psnawp's defaults: same-name matching against the user's games.
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                .defaultHeader("Country", "US");
    }

    /** False when PSN_NPSSO is blank. */
    public boolean isConfigured() {
        return authenticator.isConfigured();
    }

    /** True while Sony's last 429 still applies; every call fails fast with PSN_RATE_LIMITED meanwhile. */
    public boolean isRateLimited() {
        Instant until = rateLimitedUntil;
        return until != null && clock.instant().isBefore(until);
    }

    /** When Sony started rejecting the service account (expired NPSSO); null while healthy. */
    public Instant serviceAuthFailingSince() {
        return authenticator.authFailingSince();
    }

    /**
     * Resolves an online ID to its account.
     *
     * @throws PsnIntegrationException PSN_USER_NOT_FOUND when no account has that online ID;
     *                                 PSN_PROFILE_PRIVATE when its privacy settings hide it
     */
    public PsnAccount findAccount(String onlineId) {
        if (!isConfigured()) throw PsnIntegrationException.notConfigured();

        JsonNode profile;
        try {
            profile = get("profile", PROFILE_URL, onlineId).path("profile");
        } catch (UpstreamError e) {
            if (e.status == HttpStatus.NOT_FOUND.value() || e.sonyCode == USER_NOT_FOUND_ERROR) {
                throw PsnIntegrationException.userNotFound();
            }
            if (e.status == HttpStatus.FORBIDDEN.value() || e.sonyCode == ACCESS_DENIED_ERROR) {
                throw PsnIntegrationException.profilePrivate();
            }
            throw e.toIntegrationException("profile");
        }

        String accountId = profile.path("accountId").asText("");
        if (accountId.isEmpty()) {
            log.warn("PSN profile call returned no accountId");
            throw PsnIntegrationException.unavailable();
        }
        String currentOnlineId = profile.path("currentOnlineId").asText("");
        if (currentOnlineId.isEmpty()) {
            currentOnlineId = profile.path("onlineId").asText(onlineId);
        }
        return new PsnAccount(accountId, currentOnlineId);
    }

    /** Every PS4/PS5 title the account has played, following the offset pagination to the end. */
    public PsnPlayedGamesResult getPlayedGames(String accountId) {
        if (!isConfigured()) throw PsnIntegrationException.notConfigured();

        List<PsnPlayedTitle> titles = new ArrayList<>();
        int offset = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode response;
            try {
                response = get("gamelist", TITLES_URL, accountId, PAGE_SIZE, offset);
            } catch (UpstreamError e) {
                if (e.status == HttpStatus.FORBIDDEN.value() || e.sonyCode == ACCESS_DENIED_ERROR) {
                    return PsnPlayedGamesResult.privateProfile();
                }
                if (e.status == HttpStatus.NOT_FOUND.value()) {
                    throw PsnIntegrationException.userNotFound();
                }
                throw e.toIntegrationException("gamelist");
            }

            JsonNode items = response.path("titles");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    PsnPlayedTitle title = toTitle(item);
                    if (title != null) titles.add(title);
                }
            }

            int nextOffset = response.path("nextOffset").asInt(0);
            if (nextOffset <= offset || !items.isArray() || items.isEmpty()) {
                return new PsnPlayedGamesResult(false, titles);
            }
            offset = nextOffset;
        }
        log.warn("PSN gamelist pagination stopped after {} pages", MAX_PAGES);
        return new PsnPlayedGamesResult(false, titles);
    }

    /** @return null for titles that are not PS4/PS5 games or carry no id */
    static PsnPlayedTitle toTitle(JsonNode item) {
        ExternalPlatform platform = platformOf(item.path("category").asText(""));
        String titleId = item.path("titleId").asText("");
        if (platform == null || titleId.isEmpty()) {
            return null;
        }
        String imageUrl = item.path("imageUrl").asText("");
        return new PsnPlayedTitle(
                titleId,
                item.path("name").asText("Unknown"),
                imageUrl.isEmpty() ? null : imageUrl,
                platform,
                item.path("playCount").asInt(0),
                PsnPlayDuration.toMinutes(item.path("playDuration").asText(null)),
                instant(item.path("firstPlayedDateTime").asText(null)),
                instant(item.path("lastPlayedDateTime").asText(null)),
                conceptId(item.path("concept").path("id")));
    }

    /** {@code concept.id} as a positive number (PSN sends it as a JSON number); null when missing or malformed. */
    static Long conceptId(JsonNode id) {
        long value = id.isIntegralNumber() ? id.asLong()
                : id.isTextual() && id.asText().matches("\\d{1,18}") ? Long.parseLong(id.asText()) : 0;
        return value > 0 ? value : null;
    }

    /** PSN title category → Pytra link platform; null for anything that is not a PS4/PS5 game. */
    public static ExternalPlatform platformOf(String category) {
        if (PS5_CATEGORY.equals(category)) return ExternalPlatform.PSN_PS5;
        if (PS4_CATEGORY.equals(category)) return ExternalPlatform.PSN_PS4;
        return null;
    }

    static Instant instant(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isBlank()) return null;
        try {
            return Instant.parse(isoDateTime);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ---- HTTP ----------------------------------------------------------------------------

    /**
     * GET with the service account's bearer token. A 401 means the token died early: it is renewed
     * and the call retried once; a second 401 means the service account itself is rejected.
     */
    private JsonNode get(String purpose, String uriTemplate, Object... uriVariables) {
        for (int attempt = 1; ; attempt++) {
            if (isRateLimited()) {
                throw PsnIntegrationException.rateLimited();
            }
            String token = accessToken();
            String json;
            try {
                json = restClient.get()
                        .uri(uriTemplate, uriVariables)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == HttpStatus.UNAUTHORIZED.value()) {
                    authenticator.invalidate(token);
                    if (attempt == 1) continue;
                    authenticator.reportRejectedAccessToken();
                    throw PsnIntegrationException.unavailable();
                }
                if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                    startRateLimitCooldown(e.getResponseHeaders() != null
                            ? e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER) : null, purpose);
                    throw PsnIntegrationException.rateLimited();
                }
                throw new UpstreamError(status, sonyErrorCode(e.getResponseBodyAsString()));
            } catch (RestClientException e) {
                // Timeouts / connection errors. Class name only: the message may embed request details.
                log.warn("PSN {} call failed: {}", purpose, e.getClass().getSimpleName());
                throw PsnIntegrationException.unavailable();
            }

            JsonNode body = parse(json, purpose);
            // Some PSN endpoints answer 200 with an error object instead of a 4xx.
            if (body.has("error")) {
                throw new UpstreamError(HttpStatus.OK.value(), body.path("error").path("code").asInt(0));
            }
            return body;
        }
    }

    /** The sign-in endpoints are rate-limited too: their 429 starts the same cooldown. */
    private String accessToken() {
        try {
            return authenticator.accessToken();
        } catch (PsnIntegrationException e) {
            if (PsnIntegrationException.RATE_LIMITED.equals(e.getCode())) {
                startRateLimitCooldown(null, "sign-in");
            }
            throw e;
        }
    }

    private void startRateLimitCooldown(String retryAfter, String purpose) {
        Instant now = clock.instant();
        Duration cooldown = retryAfter(retryAfter, now);
        rateLimitedUntil = now.plus(cooldown);
        log.warn("PSN {} call was rate limited; pausing every PSN call for {} s", purpose, cooldown.toSeconds());
    }

    /**
     * {@code Retry-After} as delay-seconds or an HTTP date, clamped to [1 min, 1 h];
     * {@link #DEFAULT_RATE_LIMIT_COOLDOWN} when missing or unparseable.
     */
    static Duration retryAfter(String header, Instant now) {
        Duration delay = null;
        if (header != null && !header.isBlank()) {
            String value = header.strip();
            try {
                delay = Duration.ofSeconds(Long.parseLong(value));
            } catch (NumberFormatException notSeconds) {
                try {
                    delay = Duration.between(now, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                } catch (DateTimeParseException notADate) {
                    delay = null;
                }
            }
        }
        if (delay == null) return DEFAULT_RATE_LIMIT_COOLDOWN;
        if (delay.compareTo(MIN_RATE_LIMIT_COOLDOWN) < 0) return MIN_RATE_LIMIT_COOLDOWN;
        if (delay.compareTo(MAX_RATE_LIMIT_COOLDOWN) > 0) return MAX_RATE_LIMIT_COOLDOWN;
        return delay;
    }

    private int sonyErrorCode(String body) {
        if (body == null || body.isBlank()) return 0;
        try {
            return objectMapper.readTree(body).path("error").path("code").asInt(0);
        } catch (Exception e) {
            return 0;
        }
    }

    private JsonNode parse(String json, String purpose) {
        if (json == null || json.isBlank()) {
            log.warn("PSN {} call returned an empty body", purpose);
            throw PsnIntegrationException.unavailable();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("PSN {} call returned an unparseable body", purpose);
            throw PsnIntegrationException.unavailable();
        }
    }

    /** Non-401 error answer from PSN; each call decides what it means. */
    private static final class UpstreamError extends RuntimeException {
        final int status;
        final int sonyCode;

        UpstreamError(int status, int sonyCode) {
            super(null, null, false, false);
            this.status = status;
            this.sonyCode = sonyCode;
        }

        PsnIntegrationException toIntegrationException(String purpose) {
            log.warn("PSN {} call answered HTTP {} (PSN error code {})", purpose, status, sonyCode);
            if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                return PsnIntegrationException.rateLimited();
            }
            return PsnIntegrationException.unavailable();
        }
    }
}
