package com.estebanmm13.pytra_api.covers.client;

import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Fetches Steam CDN images with strict limits: no redirects (a redirect could point outside the
 * host allowlist), 3s connect / 5s total timeouts, image content types only and a hard size cap
 * enforced while streaming (Content-Length can't be trusted).
 */
@Slf4j
@Component
public class HttpCoverImageFetcher implements CoverImageFetcher {

    static final int MAX_SOURCE_BYTES = 2 * 1024 * 1024;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override
    public byte[] fetch(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "image/*")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (status == 404) {
                    throw new ResourceNotFoundException(SteamIntegrationException.COVER_NOT_FOUND);
                }
                if (status != 200) {
                    log.warn("Cover fetch failed with HTTP {} from {}", status, uri.getHost());
                    throw SteamIntegrationException.coverUnavailable();
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (!contentType.startsWith("image/") || declaredLength > MAX_SOURCE_BYTES) {
                    throw SteamIntegrationException.coverUnavailable();
                }
                byte[] bytes = body.readNBytes(MAX_SOURCE_BYTES + 1);
                if (bytes.length > MAX_SOURCE_BYTES) {
                    throw SteamIntegrationException.coverUnavailable();
                }
                return bytes;
            }
        } catch (IOException e) {
            log.warn("Cover fetch failed from {}: {}", uri.getHost(), e.toString());
            throw SteamIntegrationException.coverUnavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw SteamIntegrationException.coverUnavailable();
        }
    }
}
