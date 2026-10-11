package com.estebanmm13.pytra_api.metadata.client;

/**
 * Steam cover art URLs. Pytra shows covers as portrait box art (2:3), so Steam games use the
 * library capsule ({@code library_600x900.jpg}) instead of the landscape store header. The URL is
 * built from the appid on the public CDN (no API key, no extra request); a few old or tiny apps
 * have no library capsule, and the web falls back to the header image when it fails to load.
 */
public final class SteamCoverImages {

    private static final String PORTRAIT_COVER_URL =
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/%s/library_600x900.jpg";

    private SteamCoverImages() {
    }

    /** Portrait library capsule (600x900) for a Steam appid. */
    public static String portraitCoverUrl(long appId) {
        return PORTRAIT_COVER_URL.formatted(appId);
    }

    /** Same as {@link #portraitCoverUrl(long)}, for appids stored as text (game_platform_links.external_id). */
    public static String portraitCoverUrl(String appId) {
        return PORTRAIT_COVER_URL.formatted(appId);
    }
}
