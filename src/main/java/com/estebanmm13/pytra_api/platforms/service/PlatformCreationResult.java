package com.estebanmm13.pytra_api.platforms.service;

import com.estebanmm13.pytra_api.platforms.dto.PlatformResponseDto;

/** @param created false when an existing entry matched the name case-insensitively */
public record PlatformCreationResult(PlatformResponseDto platform, boolean created) {
}
