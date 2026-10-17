package com.estebanmm13.pytra_api.psnsync.client;

import java.util.regex.Pattern;

/** PSN online IDs: 3 to 16 characters, letters, digits, hyphens and underscores. */
public final class PsnOnlineIds {

    private static final Pattern ONLINE_ID = Pattern.compile("^[A-Za-z0-9_-]{3,16}$");

    private PsnOnlineIds() {
    }

    /** @return the trimmed online ID, or null when it is not a valid PSN online ID */
    public static String normalize(String onlineId) {
        if (onlineId == null) return null;
        String trimmed = onlineId.strip();
        return ONLINE_ID.matcher(trimmed).matches() ? trimmed : null;
    }
}
