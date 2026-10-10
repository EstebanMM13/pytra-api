package com.estebanmm13.pytra_api.metadata.client;

import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.metadata.dto.SteamGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.SteamStoreSearchResultDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Client for the public (keyless, undocumented) Steam store API, used to prefill the game form.
 *
 * <p>Store texts are requested in Spanish for the Spanish storefront, so genre names match the
 * language users type their own genres in. Every failure (HTTP error, timeout, garbage body,
 * rate limit) becomes a 503 STEAM_UNAVAILABLE: for an optional autofill the client only needs to
 * know "try again later".
 */
@Slf4j
@Component
public class SteamStoreClient {

    static final String BASE_URL = "https://store.steampowered.com";
    static final String SEARCH_PATH = "/api/storesearch/";
    static final String DETAILS_PATH = "/api/appdetails";
    private static final String LANGUAGE = "spanish";
    private static final String COUNTRY = "ES";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    public SteamStoreClient() {
        this(configure(RestClient.builder()).build());
    }

    SteamStoreClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Shorter timeouts than the sync client (3s connect / 5s read): a user is waiting on the form. */
    static RestClient.Builder configure(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return builder.baseUrl(BASE_URL).requestFactory(requestFactory);
    }

    public List<SteamStoreSearchResultDto> search(String term, int limit) {
        JsonNode root = get(SEARCH_PATH, uriBuilder -> uriBuilder.path(SEARCH_PATH)
                .queryParam("term", term)
                .queryParam("l", LANGUAGE)
                .queryParam("cc", COUNTRY)
                .build());
        return SteamStoreParser.parseSearch(root, limit);
    }

    public SteamGameMetadataDto getDetails(long appId) {
        JsonNode root = get(DETAILS_PATH, uriBuilder -> uriBuilder.path(DETAILS_PATH)
                .queryParam("appids", appId)
                .queryParam("l", LANGUAGE)
                .queryParam("cc", COUNTRY)
                .build());
        return SteamStoreParser.parseDetails(root, appId);
    }

    private JsonNode get(String path, Function<UriBuilder, URI> uri) {
        String json;
        try {
            json = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            // The store answers 429 (sometimes 403) when rate limiting.
            log.warn("Steam store call {} answered HTTP {}", path, e.getStatusCode().value());
            throw SteamIntegrationException.unavailable();
        } catch (RestClientException e) {
            log.warn("Steam store call {} failed: {}", path, e.getClass().getSimpleName());
            throw SteamIntegrationException.unavailable();
        }
        if (json == null || json.isBlank()) {
            log.warn("Steam store call {} returned an empty body", path);
            throw SteamIntegrationException.unavailable();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Steam store call {} returned an unparseable body", path);
            throw SteamIntegrationException.unavailable();
        }
    }
}
