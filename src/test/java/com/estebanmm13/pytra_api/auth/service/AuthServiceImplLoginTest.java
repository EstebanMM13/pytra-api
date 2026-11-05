package com.estebanmm13.pytra_api.auth.service;

import com.estebanmm13.pytra_api.account.repository.PersonalAccessTokenRepository;
import com.estebanmm13.pytra_api.auth.dto.login.LoginRequestDto;
import com.estebanmm13.pytra_api.auth.mapper.UserMapper;
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
import com.estebanmm13.pytra_api.error.EmailNotVerifiedException;
import com.estebanmm13.pytra_api.error.InvalidCredentialException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Login by email or username: every failure before the password check answers the same "Invalid credentials"
 * (and still spends a hash comparison), and email verification is only revealed to the right password.
 */
class AuthServiceImplLoginTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final TokenGenerator tokenGenerator = mock(TokenGenerator.class);

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        when(tokenGenerator.generateTokenRaw()).thenReturn("raw");
        when(passwordEncoder.encode("raw")).thenReturn("dummy-hash");
        service = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, mock(UserMapper.class),
                mock(EmailVerificationTokenRepository.class), tokenGenerator, mock(PasswordResetTokenRepository.class),
                mock(ExchangeCodeTokenRepository.class), mock(AuthEmailService.class), mock(RegistrationPolicy.class),
                new DemoMode(""), mock(PersonalAccessTokenRepository.class));
        service.initDummyPasswordHash();
    }

    private static LoginRequestDto login(String identifier, String password) {
        LoginRequestDto dto = new LoginRequestDto();
        dto.setIdentifier(identifier);
        dto.setPassword(password);
        return dto;
    }

    private static User user(String passwordHash, boolean verified) {
        return User.builder().id(5L).username("owner").email("owner@pytra.test").usernameDisplay("Owner")
                .passwordHash(passwordHash).emailVerified(verified).createdAt(LocalDateTime.now()).role(Role.USER).build();
    }

    @Test
    void loginByEmailIsCaseInsensitiveAndReturnsASessionToken() {
        when(userRepository.findByEmail("owner@pytra.test")).thenReturn(Optional.of(user("hash", true)));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jwtService.generateTokenWithRole("owner", "USER", 5L)).thenReturn("jwt");

        assertThat(service.login(login("  Owner@Pytra.TEST ", "secret")).getToken()).isEqualTo("jwt");
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void loginByUsernameReturnsASessionToken() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(user("hash", true)));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jwtService.generateTokenWithRole("owner", "USER", 5L)).thenReturn("jwt");

        assertThat(service.login(login("owner", "secret")).getToken()).isEqualTo("jwt");
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void unknownAccountIsInvalidCredentialsAfterADummyHashCheck() {
        when(userRepository.findByEmail("ghost@pytra.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(login("ghost@pytra.test", "secret")))
                .isInstanceOf(InvalidCredentialException.class).hasMessage("Invalid credentials");
        verify(passwordEncoder).matches("secret", "dummy-hash");
    }

    @Test
    void googleOnlyAccountWithoutPasswordIsInvalidCredentials() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(user(null, true)));

        assertThatThrownBy(() -> service.login(login("owner", "secret")))
                .isInstanceOf(InvalidCredentialException.class).hasMessage("Invalid credentials");
        verify(passwordEncoder).matches("secret", "dummy-hash");
    }

    @Test
    void wrongPasswordIsInvalidCredentialsEvenForAnUnverifiedEmail() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(user("hash", false)));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(login("owner", "wrong")))
                .isInstanceOf(InvalidCredentialException.class).hasMessage("Invalid credentials");
        verify(jwtService, never()).generateTokenWithRole(anyString(), anyString(), anyLong());
    }

    @Test
    void rightPasswordOnAnUnverifiedEmailIsEmailNotVerified() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(user("hash", false)));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(login("owner", "secret")))
                .isInstanceOf(EmailNotVerifiedException.class);
        verify(jwtService, never()).generateTokenWithRole(anyString(), anyString(), anyLong());
    }
}
