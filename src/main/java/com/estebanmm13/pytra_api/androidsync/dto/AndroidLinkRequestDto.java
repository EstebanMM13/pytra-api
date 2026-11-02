package com.estebanmm13.pytra_api.androidsync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Links an installed Android app (its package name) to one of the user's games. */
public record AndroidLinkRequestDto(
        @NotBlank @Size(max = 255) @Pattern(regexp = AndroidLinkRequestDto.PACKAGE_NAME) String packageName,
        @NotNull Long gameId) {

    /** Android application id: dot-separated segments of letters, digits and underscores. */
    public static final String PACKAGE_NAME = "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+";
}
