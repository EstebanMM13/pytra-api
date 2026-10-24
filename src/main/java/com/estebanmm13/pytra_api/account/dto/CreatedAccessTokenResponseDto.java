package com.estebanmm13.pytra_api.account.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** Answer to a token creation: the only response that ever contains the plaintext token. */
@Getter
@AllArgsConstructor
public class CreatedAccessTokenResponseDto {
    private Long id;
    private String name;
    private String token;
    private LocalDateTime createdAt;
}
