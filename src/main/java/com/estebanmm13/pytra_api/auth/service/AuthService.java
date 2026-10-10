package com.estebanmm13.pytra_api.auth.service;

import com.estebanmm13.pytra_api.auth.dto.exchangeToken.ExchangeCodeTokenRequestDto;
import com.estebanmm13.pytra_api.auth.dto.forgotPassword.ForgotPasswordRequestDto;
import com.estebanmm13.pytra_api.auth.dto.forgotPassword.ForgotPasswordResponseDto;
import com.estebanmm13.pytra_api.auth.dto.login.LoginRequestDto;
import com.estebanmm13.pytra_api.auth.dto.login.LoginResponseDto;
import com.estebanmm13.pytra_api.auth.dto.register.RegisterRequestDto;
import com.estebanmm13.pytra_api.auth.dto.register.RegisterResponseDto;
import com.estebanmm13.pytra_api.auth.dto.resendVerification.ResendVerificationRequestDto;
import com.estebanmm13.pytra_api.auth.dto.resendVerification.ResendVerificationResponseDto;
import com.estebanmm13.pytra_api.auth.dto.resetPassword.ResetPasswordRequestDto;
import com.estebanmm13.pytra_api.auth.model.ExchangeCodeToken;

public interface AuthService {
    RegisterResponseDto register(RegisterRequestDto registerRequestDto);
    LoginResponseDto login(LoginRequestDto loginRequestDto);
    void verifyEmail(String rawToken);
    ResendVerificationResponseDto resendVerification(ResendVerificationRequestDto resendVerificationRequestDto);
    ForgotPasswordResponseDto forgotPassword(ForgotPasswordRequestDto forgotPasswordRequestDto);
    void resetPassword(ResetPasswordRequestDto requestPasswordRequestDto);
    LoginResponseDto exchangeCodeToken(ExchangeCodeTokenRequestDto exchangeCodeTokenRequestDto);
    LoginResponseDto demoLogin();

}
