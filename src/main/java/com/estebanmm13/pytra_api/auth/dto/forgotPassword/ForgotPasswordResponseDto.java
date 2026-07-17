package com.estebanmm13.pytra_api.auth.dto.forgotPassword;

import lombok.Getter;

@Getter
public class ForgotPasswordResponseDto {
    private final String message = "If this email exists, we have sent you a recovery link";
}

