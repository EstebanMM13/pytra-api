package com.estebanmm13.pytra_api.auth.service;

import com.estebanmm13.pytra_api.account.repository.PersonalAccessTokenRepository;
import com.estebanmm13.pytra_api.auth.dto.exchangeToken.ExchangeCodeTokenRequestDto;
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
import com.estebanmm13.pytra_api.error.InvalidTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One-time auth tokens are consumed atomically (a conditional UPDATE decides the single winner),
 * and a password reset revokes every JWT issued before it.
 */
class AuthServiceImplTokenConsumptionTest {

    private static final long TOKEN_ID = 11L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final TokenGenerator tokenGenerator = mock(TokenGenerator.class);
    private final PasswordResetTokenRepository passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
    private final ExchangeCodeTokenRepository exchangeCodeTokenRepository = mock(ExchangeCodeTokenRepository.class);
    private final PersonalAccessTokenRepository personalAccessTokenRepository = mock(PersonalAccessTokenRepository.class);

    private final AuthServiceImpl service = new AuthServiceImpl(userRepository, passwordEncoder, jwtService,
            mock(UserMapper.class), mock(EmailVerificationTokenRepository.class), tokenGenerator,
            passwordResetTokenRepository, exchangeCodeTokenRepository, mock(AuthEmailService.class),
            mock(RegistrationPolicy.class), new DemoMode(""), personalAccessTokenRepository);

    private static User user() {
        return User.builder().id(5L).username("owner").email("owner@pytra.test").usernameDisplay("owner")
                .emailVerified(true).createdAt(LocalDateTime.now()).role(Role.USER).passwordHash("old").build();
    }

    private ResetPasswordRequestDto resetRequest() {
        ResetPasswordRequestDto dto = new ResetPasswordRequestDto();
        dto.setToken("raw-reset");
        dto.setNewPassword("new-password");
        when(tokenGenerator.hashToken("raw-reset")).thenReturn("reset-hash");
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        return dto;
    }

    private User givenValidResetToken() {
        User user = user();
        when(passwordResetTokenRepository.findByTokenHash("reset-hash")).thenReturn(Optional.of(
                PasswordResetToken.builder().id(TOKEN_ID).user(user).tokenHash("reset-hash")
                        .expiresAt(LocalDateTime.now().plusMinutes(30)).build()));
        return user;
    }

    private ExchangeCodeTokenRequestDto exchangeRequest() {
        ExchangeCodeTokenRequestDto dto = new ExchangeCodeTokenRequestDto();
        dto.setCode("raw-code");
        when(tokenGenerator.hashToken("raw-code")).thenReturn("code-hash");
        when(exchangeCodeTokenRepository.findByTokenHash("code-hash")).thenReturn(Optional.of(
                ExchangeCodeToken.builder().id(TOKEN_ID).user(user()).tokenHash("code-hash")
                        .expiresAt(LocalDateTime.now().plusMinutes(1)).build()));
        return dto;
    }

    @Test
    void resetPasswordRevokesEveryJwtIssuedBeforeIt() {
        ResetPasswordRequestDto dto = resetRequest();
        User user = givenValidResetToken();
        when(passwordResetTokenRepository.markConsumed(eq(TOKEN_ID), any())).thenReturn(1);
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        service.resetPassword(dto);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        // JWT iat has second precision: the cut-off is truncated so a login right after the reset still works.
        assertThat(user.getCredentialsChangedAt()).isNotNull()
                .isBetween(before, Instant.now())
                .isEqualTo(user.getCredentialsChangedAt().truncatedTo(ChronoUnit.SECONDS));
        verify(userRepository).save(user);
        verify(personalAccessTokenRepository).deleteAllByUserId(5L);
    }

    @Test
    void resetTokenConsumedByAConcurrentRequestIsRejectedWithoutChangingThePassword() {
        ResetPasswordRequestDto dto = resetRequest();
        User user = givenValidResetToken();
        when(passwordResetTokenRepository.markConsumed(eq(TOKEN_ID), any())).thenReturn(0);

        assertThatThrownBy(() -> service.resetPassword(dto))
                .isInstanceOf(InvalidTokenException.class).hasMessage("Invalid token");

        assertThat(user.getPasswordHash()).isEqualTo("old");
        assertThat(user.getCredentialsChangedAt()).isNull();
        verify(userRepository, never()).save(any());
        verify(personalAccessTokenRepository, never()).deleteAllByUserId(anyLong());
    }

    @Test
    void exchangeCodeIssuesAJwtWhenThisRequestConsumesTheCode() {
        ExchangeCodeTokenRequestDto dto = exchangeRequest();
        when(exchangeCodeTokenRepository.markConsumed(eq(TOKEN_ID), any())).thenReturn(1);
        when(jwtService.generateTokenWithRole("owner", "USER", 5L)).thenReturn("jwt");

        assertThat(service.exchangeCodeToken(dto).getToken()).isEqualTo("jwt");
    }

    @Test
    void exchangeCodeConsumedByAConcurrentRequestIssuesNoJwt() {
        ExchangeCodeTokenRequestDto dto = exchangeRequest();
        when(exchangeCodeTokenRepository.markConsumed(eq(TOKEN_ID), any())).thenReturn(0);

        assertThatThrownBy(() -> service.exchangeCodeToken(dto))
                .isInstanceOf(InvalidTokenException.class).hasMessage("Invalid token");

        verify(jwtService, never()).generateTokenWithRole(anyString(), anyString(), anyLong());
    }
}
