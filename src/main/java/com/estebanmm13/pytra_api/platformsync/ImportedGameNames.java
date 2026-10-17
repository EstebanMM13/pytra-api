package com.estebanmm13.pytra_api.platformsync;

import java.util.Locale;

/** Name handling shared by the platform importers when they match or create games. */
public final class ImportedGameNames {

    /** games.name is VARCHAR(255). */
    static final int MAX_NAME_LENGTH = 255;

    private ImportedGameNames() {
    }

    /** Stripped name that fits games.name; "Unknown" when the platform sent none. */
    public static String truncate(String name) {
        String safe = (name == null || name.isBlank()) ? "Unknown" : name.strip();
        return safe.length() <= MAX_NAME_LENGTH ? safe : safe.substring(0, MAX_NAME_LENGTH);
    }

    /** Mirrors the unique index on (user_id, LOWER(name)). */
    public static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
