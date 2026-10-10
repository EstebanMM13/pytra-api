package com.estebanmm13.pytra_api.auth.service;

import com.estebanmm13.pytra_api.auth.dto.exchangeToken.ExchangeCodeTokenRequestDto;
import com.estebanmm13.pytra_api.auth.dto.forgotPassword.ForgotPasswordRequestDto;
import com.estebanmm13.pytra_api.auth.dto.login.LoginRequestDto;
import com.estebanmm13.pytra_api.auth.dto.resendVerification.ResendVerificationRequestDto;
import com.estebanmm13.pytra_api.auth.dto.resetPassword.ResetPasswordRequestDto;
import com.estebanmm13.pytra_api.auth.mapper.UserMapper;
import com.estebanmm13.pytra_api.auth.model.ExchangeCodeToken;
import com.estebanmm13.pytra_api.auth.model.PasswordResetToken;
import com.estebanmm13.pytra_api.auth.model.Role;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.EmailVerificationTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.ExchangeCodeTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.PasswordResetTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.auth.security.DemoMode;
import com.estebanmm13.pytra_api.auth.security.JwtService;
import com.estebanmm13.pytra_api.auth.security.TokenGenerator;
import com.estebanmm13.pytra_api.auth.validation.RegistrationPolicy;
import com.estebanmm13.pytra_api.error.InvalidCredentialException;
import com.estebanmm13.pytra_api.error.InvalidTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The demo account (DEMO_USER_ID) is demo-only: every credential path treats it like an unknown account. */
class AuthServiceImplDemoUserTest {

    private static final long DEMO_ID = 7L;
    private static final String DEMO_EMAIL = "owner@pytra.test";
    private static final String PASSWORD = "correct-password";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final EmailVerificationTokenRepository emailVerificationTokenRepository = mock(EmailVerificationTokenRepository.class);
    private final TokenGenerator tokenGenerator = mock(TokenGenerator.class);
    private final PasswordResetTokenRepository passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
    private final ExchangeCodeTokenRepository exchangeCodeTokenRepository = mock(ExchangeCodeTokenRepository.class);
    private final AuthEmailService authEmailService = mock(AuthEmailService.class);

    private AuthServiceImpl service(DemoMode demoMode) {
        AuthServiceImpl service = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, mock(UserMapper.class),
                emailVerificationTokenRepository, tokenGenerator, passwordResetTokenRepository,
                exchangeCodeTokenRepository, authEmailService, mock(RegistrationPolicy.class), demoMode);
        when(tokenGenerator.generateTokenRaw()).thenReturn("raw");
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        service.initDummyPasswordHash();
        return service;
    }

    private static DemoMode demoEnabled() {
        return new DemoMode(String.valueOf(DEMO_ID));
    }

    private static User demoUser(boolean emailVerified) {
        return User.builder()
                .id(DEMO_ID)
                .email(DEMO_EMAIL)
                .username("demo")
                .usernameDisplay("demo")
                .passwordHash("stored-hash")
                .emailVerified(emailVerified)
                .createdAt(LocalDateTime.now())
                .role(Role.USER)
                .build();
    }

    private static LoginRequestDto loginRequest(String identifier) {
        LoginRequestDto request = new LoginRequestDto();
        request.setIdentifier(identifier);
        request.setPassword(PASSWORD);
        return request;
    }

    @Test
    void passwordLoginIsRejectedLikeBadCredentials() {
        AuthServiceImpl service = service(demoEnabled());
        when(userRepository.findByEmail(DEMO_EMAIL)).thenReturn(Optional.of(demoUser(true)));
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(demoUser(true)));
        when(passwordEncoder.matches(PASSWORD, "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(loginRequest(DEMO_EMAIL)))
                .isInstanceOf(InvalidCredentialException.class).hasMessage("Invalid credentials");
        assertThatThrownBy(() -> service.login(loginRequest("demo")))
                .isInstanceOf(InvalidCredentialException.class).hasMessage("Invalid credentials");
        verifyNoInteractions(jwtService);
    }

    @Test
    void passwordLoginWorksForThatAccountWhenDemoIsDisabled() {
        AuthServiceImpl service = service(new DemoMode(""));
        when(userRepository.findByEmail(DEMO_EMAIL)).thenReturn(Optional.of(demoUser(true)));
        when(passwordEncoder.matches(PASSWORD, "stored-hash")).thenReturn(true);
        when(jwtService.generateTokenWithRole("demo", "USER", DEMO_ID)).thenReturn("jwt");

        assertThat(service.login(loginRequest(DEMO_EMAIL)).getToken()).isEqualTo("jwt");
    }

    @Test
    void forgotPasswordAnswersNormallyButSendsNothing() {
        AuthServiceImpl service = service(demoEnabled());
        when(userRepository.findByEmailForUpdate(DEMO_EMAIL)).thenReturn(Optional.of(demoUser(true)));
        ForgotPasswordRequestDto request = new ForgotPasswordRequestDto();
        request.setEmail(DEMO_EMAIL);

        assertThat(service.forgotPassword(request)).isNotNull();
        verifyNoInteractions(authEmailService);
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void resendVerificationAnswersNormallyButSendsNothing() {
        AuthServiceImpl service = service(demoEnabled());
        when(userRepository.findByEmailForUpdate(DEMO_EMAIL)).thenReturn(Optional.of(demoUser(false)));
        ResendVerificationRequestDto request = new ResendVerificationRequestDto();
        request.setEmail(DEMO_EMAIL);

        assertThat(service.resendVerification(request)).isNotNull();
        verifyNoInteractions(authEmailService);
        verify(emailVerificationTokenRepository, never()).save(any());
    }

    @Test
    void resetPasswordTokenCannotChangeTheDemoPassword() {
        AuthServiceImpl service = service(demoEnabled());
        User demo = demoUser(true);
        when(tokenGenerator.hashToken("reset")).thenReturn("reset-hash");
        when(passwordResetTokenRepository.findByTokenHash("reset-hash")).thenReturn(Optional.of(PasswordResetToken.builder()
                .user(demo).tokenHash("reset-hash").expiresAt(LocalDateTime.now().plusHours(1)).build()));
        ResetPasswordRequestDto request = new ResetPasswordRequestDto();
        request.setToken("reset");
        request.setNewPassword("new-password-123");

        assertThatThrownBy(() -> service.resetPassword(request))
                .isInstanceOf(InvalidTokenException.class).hasMessage("Invalid token");
        assertThat(demo.getPasswordHash()).isEqualTo("stored-hash");
        verify(userRepository, never()).save(any());
    }

    @Test
    void exchangeCodeNeverIssuesATokenForTheDemoAccount() {
        AuthServiceImpl service = service(demoEnabled());
        when(tokenGenerator.hashToken("code")).thenReturn("code-hash");
        when(exchangeCodeTokenRepository.findByTokenHash("code-hash")).thenReturn(Optional.of(ExchangeCodeToken.builder()
                .user(demoUser(true)).tokenHash("code-hash").expiresAt(LocalDateTime.now().plusMinutes(1)).build()));
        ExchangeCodeTokenRequestDto request = new ExchangeCodeTokenRequestDto();
        request.setCode("code");

        assertThatThrownBy(() -> service.exchangeCodeToken(request))
                .isInstanceOf(InvalidTokenException.class).hasMessage("Invalid token");
        verify(jwtService, never()).generateTokenWithRole(anyString(), anyString(), anyLong());
    }
}
