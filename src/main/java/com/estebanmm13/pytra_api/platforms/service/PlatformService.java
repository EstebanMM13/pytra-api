package com.estebanmm13.pytra_api.platforms.service;

import com.estebanmm13.pytra_api.platforms.dto.PlatformRequestDto;
import com.estebanmm13.pytra_api.platforms.dto.PlatformResponseDto;
import com.estebanmm13.pytra_api.platforms.model.Platform;

import java.util.List;

public interface PlatformService {

    /** The whole catalog in catalog order. */
    List<PlatformResponseDto> findAll();

    /**
     * Returns the existing entry on a case-insensitive name match, otherwise creates it on behalf of
     * {@code userId}. Only actual creations count toward the per-user rate limit and the catalog cap.
     *
     * @throws com.estebanmm13.pytra_api.error.ResourceLimitExceededException PLATFORM_CATALOG_FULL when the catalog is full
     * @throws com.estebanmm13.pytra_api.error.TooManyRequestsException PLATFORM_RATE_LIMITED when the user creates too many
     */
    PlatformCreationResult createIfMissing(Long userId, PlatformRequestDto request);

    /**
     * Platform for a run create/update request.
     *
     * <p>{@code platformId} wins. Without it, {@code legacyCode} (old enum value) is mapped through the
     * catalog. When updating ({@code current} not null), a legacy value equal to the current platform's
     * legacy projection keeps the current platform, so an old client that can only echo "OTHER" never
     * overwrites a platform it cannot represent.
     *
     * @throws com.estebanmm13.pytra_api.error.InvalidRequestException INVALID_PLATFORM when nothing resolves
     */
    Platform resolve(Long platformId, String legacyCode, Platform current);

    /** Catalog entry for an old enum value; fails when the seed is missing. */
    Platform requireByLegacyCode(String legacyCode);
}
