package com.estebanmm13.pytra_api.platforms.dto;

/** Compact platform reference embedded in other responses (for example the platforms of a game). */
public record PlatformRefDto(Long id, String name, String color) {
}
