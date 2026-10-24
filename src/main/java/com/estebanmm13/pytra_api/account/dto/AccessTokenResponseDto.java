package com.estebanmm13.pytra_api.account.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** A personal access token as listed on the profile; never carries the token itself. */
@Getter
@AllArgsConstructor
public class AccessTokenResponseDto {
    private Long id;
    private String name;
    /** First characters of the token, to tell tokens apart. */
    private String prefix;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
}
