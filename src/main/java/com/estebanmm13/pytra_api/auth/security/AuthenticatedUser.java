package com.estebanmm13.pytra_api.auth.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthenticatedUser {
    private String username;
    private Long userId;
}
