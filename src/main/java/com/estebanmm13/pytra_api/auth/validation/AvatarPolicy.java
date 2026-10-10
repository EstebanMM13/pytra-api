package com.estebanmm13.pytra_api.auth.validation;

import java.util.Set;

/**
 * Single source of truth for the preset profile avatars. The web client renders each key as an icon on a
 * gradient, so these keys must match the web avatar catalog exactly.
 */
public final class AvatarPolicy {

    public static final int MAX_LENGTH = 32;

    public static final Set<String> KEYS = Set.of(
            "gamepad", "joystick", "swords", "shield", "crown", "ghost",
            "skull", "rocket", "zap", "flame", "trophy", "castle");

    private AvatarPolicy() {
    }

    public static boolean isValid(String key) {
        return key != null && KEYS.contains(key);
    }
}
