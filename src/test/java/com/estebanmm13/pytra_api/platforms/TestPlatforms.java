package com.estebanmm13.pytra_api.platforms;

import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.platforms.model.PlatformFamily;

/** In-memory catalog entries mirroring the V20 seed, for unit tests that build experiences by hand. */
public final class TestPlatforms {

    public static final Platform PC = seeded(1L, "PC", PlatformFamily.PC, "PC", 10);
    public static final Platform PS5 = seeded(2L, "PS5", PlatformFamily.PLAYSTATION, "PS5", 20);
    public static final Platform XBOX = seeded(12L, "Xbox", PlatformFamily.XBOX, "XBOX", 130);
    public static final Platform SWITCH = seeded(14L, "Switch", PlatformFamily.NINTENDO, "SWITCH", 210);
    public static final Platform DS = seeded(20L, "DS", PlatformFamily.NINTENDO, null, 270);

    private TestPlatforms() {
    }

    private static Platform seeded(Long id, String name, PlatformFamily family, String legacyCode, int sortOrder) {
        return Platform.builder()
                .id(id).name(name).family(family).color(family.defaultColor())
                .legacyCode(legacyCode).sortOrder(sortOrder)
                .build();
    }
}
