package com.estebanmm13.pytra_api.games.migration;

import java.util.Map;

/** Cover URLs before/after V21 (Steam headers to portrait capsules), shared by the unit test and the IT. */
final class SteamPortraitCoverSamples {

    static final String MIGRATION = "db/migration/V21__steam_portrait_covers.sql";

    static final Map<String, String> REWRITTEN = Map.of(
            "https://cdn.cloudflare.steamstatic.com/steam/apps/620/header.jpg",
            portrait("620"),
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1245620/d98994fe44b885fd491f3ccc93e3fd6a5e0feece/header_alt_assets_3_spanish.jpg?t=1790290043",
            portrait("1245620"),
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/2778580/header.jpg?t=1787586064",
            portrait("2778580"),
            "http://cdn.akamai.steamstatic.com/steam/apps/70/header.jpg",
            portrait("70"),
            "https://steamcdn-a.akamaihd.net/steam/apps/367520/header.jpg?t=1",
            portrait("367520"),
            "https://CDN.CLOUDFLARE.STEAMSTATIC.COM/steam/apps/271590/header.jpg",
            portrait("271590"));

    /** Left exactly as they are, including the already-migrated portrait capsule. */
    static final String[] UNTOUCHED = {
            portrait("1145360"),
            "https://images.igdb.com/igdb/image/upload/t_cover_big/co4jni.jpg",
            "https://example.com/steam/apps/620/header.jpg",
            "https://evilsteamstatic.com/steam/apps/620/header.jpg",
            "https://steamstatic.com.evil.test/steam/apps/620/header.jpg",
            "https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/apps/620/icon.jpg",
            "https://example.com/mine.png"
    };

    static String portrait(String appId) {
        return "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/" + appId + "/library_600x900.jpg";
    }

    private SteamPortraitCoverSamples() {
    }
}
