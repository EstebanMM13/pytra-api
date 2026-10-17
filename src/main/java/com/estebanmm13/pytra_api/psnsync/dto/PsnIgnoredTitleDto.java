package com.estebanmm13.pytra_api.psnsync.dto;

import java.time.LocalDateTime;

/** @param platform catalog legacy code of the title's console: "PS5" or "PS4" */
public record PsnIgnoredTitleDto(String titleId, String platform, String name, LocalDateTime ignoredAt) {
}
