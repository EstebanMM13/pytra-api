package com.estebanmm13.pytra_api.covers.service;

import com.estebanmm13.pytra_api.error.InvalidRequestException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * SSRF guard for the cover thumbnail proxy: only https URLs on the Steam image CDNs are fetched.
 * Hosts are matched exactly (no suffix matching), so IP literals, look-alike domains and userinfo
 * tricks ({@code https://shared.akamai.steamstatic.com@evil.com/}) are rejected.
 */
public final class CoverUrlPolicy {

    /** Hosts Pytra stores covers from (SteamCoverImages, SteamLibraryImporter, store search) plus their CDN mirrors. */
    static final Set<String> ALLOWED_HOSTS = Set.of(
            "shared.akamai.steamstatic.com",
            "shared.cloudflare.steamstatic.com",
            "shared.fastly.steamstatic.com",
            "shared.steamstatic.com",
            "cdn.akamai.steamstatic.com",
            "cdn.cloudflare.steamstatic.com",
            "cdn.fastly.steamstatic.com",
            "cdn.steamstatic.com",
            "steamcdn-a.akamaihd.net");

    private static final int MAX_URL_LENGTH = 512;

    private CoverUrlPolicy() {
    }

    /** Returns the parsed URI, or throws 400 INVALID_COVER_URL if it may not be fetched. */
    public static URI validate(String url) {
        if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH) {
            throw invalid();
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw invalid();
        }
        String host = uri.getHost();
        boolean allowed = "https".equalsIgnoreCase(uri.getScheme())
                && uri.getRawUserInfo() == null
                && host != null
                && ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT))
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && uri.getRawPath() != null && uri.getRawPath().startsWith("/");
        if (!allowed) {
            throw invalid();
        }
        return uri;
    }

    private static InvalidRequestException invalid() {
        return new InvalidRequestException(InvalidRequestException.INVALID_COVER_URL);
    }
}
