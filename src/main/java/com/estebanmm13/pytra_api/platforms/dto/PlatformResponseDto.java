package com.estebanmm13.pytra_api.platforms.dto;

import com.estebanmm13.pytra_api.platforms.model.PlatformFamily;

/**
 * A catalog entry.
 *
 * @param legacyCode old enum value (PC, PS5, PS4, XBOX, SWITCH, MOBILE) for the entries that replaced it, else null
 */
public record PlatformResponseDto(
        Long id,
        String name,
        String color,
        PlatformFamily family,
        String legacyCode,
        Integer sortOrder) {
}
