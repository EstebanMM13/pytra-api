package com.estebanmm13.pytra_api.auth.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class AuthenticatedUser {
    private String username;
    private Long userId;
    /** {@code iat} of the JWT that authenticated this request; null if the token carries none. */
    private Instant issuedAt;
}
