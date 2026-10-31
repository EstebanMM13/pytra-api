package com.estebanmm13.pytra_api.experiences.model;

import com.estebanmm13.pytra_api.platforms.model.Platform;

/** Moves hours of one platform in an online playtime's per-platform breakdown. */
public final class OnlinePlatformHours {

    private OnlinePlatformHours() {
    }

    /**
     * Adds {@code delta} hours (negative subtracts, never below 0) to {@code platform}'s share. A platform not
     * yet played online is added when it brings hours. Null platform or zero delta: nothing changes.
     */
    public static void add(OnlinePlaytime onlinePlaytime, Platform platform, double delta) {
        if (platform == null || platform.getId() == null || delta == 0) {
            return;
        }
        boolean listed = onlinePlaytime.getPlatforms().stream().anyMatch(p -> platform.getId().equals(p.getId()));
        if (!listed) {
            if (delta < 0) {
                return;
            }
            onlinePlaytime.getPlatforms().add(platform);
        }
        double hours = onlinePlaytime.getPlatformHours().getOrDefault(platform.getId(), 0.0) + delta;
        if (hours > 1e-9) {
            onlinePlaytime.getPlatformHours().put(platform.getId(), hours);
        } else {
            onlinePlaytime.getPlatformHours().remove(platform.getId());
        }
    }
}
