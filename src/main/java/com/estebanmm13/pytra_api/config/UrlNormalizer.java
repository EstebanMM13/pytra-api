package com.estebanmm13.pytra_api.config;

/**
 * Normalizes base URLs coming from configuration so that concatenating a path
 * never produces a double slash (e.g. "https://host/" + "/oauth-callback").
 */
public final class UrlNormalizer {

    private UrlNormalizer() {
    }

    public static String normalizeBaseUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
