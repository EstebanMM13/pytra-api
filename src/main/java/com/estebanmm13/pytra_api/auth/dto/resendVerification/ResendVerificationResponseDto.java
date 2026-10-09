package com.estebanmm13.pytra_api.auth.dto.resendVerification;

import lombok.Getter;

@Getter
public class ResendVerificationResponseDto {
    private final String message = "If this email belongs to an unverified account, we have sent a new verification link";
}
