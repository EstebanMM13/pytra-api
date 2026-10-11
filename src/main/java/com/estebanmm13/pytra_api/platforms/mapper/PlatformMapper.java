package com.estebanmm13.pytra_api.platforms.mapper;

import com.estebanmm13.pytra_api.platforms.dto.PlatformRefDto;
import com.estebanmm13.pytra_api.platforms.dto.PlatformResponseDto;
import com.estebanmm13.pytra_api.platforms.model.Platform;

import java.util.Comparator;

/** Pure conversions; static so DTO builders across packages can use them without wiring. */
public final class PlatformMapper {

    /**
     * Value of the legacy {@code platform} field for clients built against the old enum when the
     * platform did not exist in it.
     */
    public static final String LEGACY_OTHER = "OTHER";

    /** Catalog order: sort_order, then name, then id. */
    public static final Comparator<Platform> CATALOG_ORDER = Comparator
            .comparing(Platform::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Platform::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Platform::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private PlatformMapper() {
    }

    public static PlatformResponseDto toResponseDto(Platform platform) {
        if (platform == null) return null;
        return new PlatformResponseDto(
                platform.getId(),
                platform.getName(),
                colorOf(platform),
                platform.getFamily(),
                platform.getLegacyCode(),
                platform.getSortOrder());
    }

    public static PlatformRefDto toRefDto(Platform platform) {
        if (platform == null) return null;
        return new PlatformRefDto(platform.getId(), platform.getName(), colorOf(platform));
    }

    /** The entry's legacy enum code, or {@link #LEGACY_OTHER} for platforms the enum did not have. */
    public static String legacyCodeOf(Platform platform) {
        if (platform == null) return null;
        return platform.getLegacyCode() != null ? platform.getLegacyCode() : LEGACY_OTHER;
    }

    public static Long idOf(Platform platform) {
        return platform != null ? platform.getId() : null;
    }

    public static String nameOf(Platform platform) {
        return platform != null ? platform.getName() : null;
    }

    /** Stored color, or the family default when none was stored. */
    public static String colorOf(Platform platform) {
        if (platform == null) return null;
        if (platform.getColor() != null) return platform.getColor();
        return platform.getFamily() != null ? platform.getFamily().defaultColor() : null;
    }
}
