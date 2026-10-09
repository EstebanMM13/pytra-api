package com.estebanmm13.pytra_api.auth.controller;

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
import com.estebanmm13.pytra_api.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDto> register(
            @Valid @RequestBody RegisterRequestDto registerRequest) {
        log.info("Registration request for user: {}", registerRequest.getUsername());
        RegisterResponseDto response = authService.register(registerRequest);
        log.info("User registered successfully: {}", registerRequest.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(
            @Valid @RequestBody LoginRequestDto loginRequestDto) {
        log.info("Login request for user: {}", loginRequestDto.getIdentifier());
        LoginResponseDto response = authService.login(loginRequestDto);
        log.info("User logged successfully: {}", loginRequestDto.getIdentifier());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(
            @RequestParam String token
    ) {
        authService.verifyEmail(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ResendVerificationResponseDto> resendVerification(
            @Valid @RequestBody ResendVerificationRequestDto resendVerificationRequestDto
    ){
        ResendVerificationResponseDto response = authService.resendVerification(resendVerificationRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponseDto> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDto forgotPasswordRequestDto
    ){
        ForgotPasswordResponseDto response = authService.forgotPassword(forgotPasswordRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDto resetPasswordRequestDto
    ){
        authService.resetPassword(resetPasswordRequestDto);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/exchange-code")
    public ResponseEntity<LoginResponseDto> exchangeCodeToken(
            @Valid @RequestBody ExchangeCodeTokenRequestDto exchangeCodeTokenRequestDto
    ){
        LoginResponseDto response = authService.exchangeCodeToken(exchangeCodeTokenRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

}
