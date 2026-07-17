package com.estebanmm13.pytra_api.auth.service;

import com.estebanmm13.pytra_api.auth.dto.forgotPassword.ForgotPasswordRequestDto;
import com.estebanmm13.pytra_api.auth.dto.forgotPassword.ForgotPasswordResponseDto;
import com.estebanmm13.pytra_api.auth.dto.login.LoginRequestDto;
import com.estebanmm13.pytra_api.auth.dto.login.LoginResponseDto;
import com.estebanmm13.pytra_api.auth.dto.register.RegisterRequestDto;
import com.estebanmm13.pytra_api.auth.dto.register.RegisterResponseDto;
import com.estebanmm13.pytra_api.auth.dto.resetPassword.ResetPasswordRequestDto;
import com.estebanmm13.pytra_api.auth.mapper.UserMapper;
import com.estebanmm13.pytra_api.auth.model.EmailVerificationToken;
import com.estebanmm13.pytra_api.auth.model.PasswordResetToken;
import com.estebanmm13.pytra_api.auth.model.Role;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.EmailVerificationTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.PasswordResetTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.auth.security.JwtService;
import com.estebanmm13.pytra_api.auth.security.TokenGenerator;
import com.estebanmm13.pytra_api.error.DuplicateResourceException;
import com.estebanmm13.pytra_api.error.EmailNotVerifiedException;
import com.estebanmm13.pytra_api.error.InvalidCredentialException;
import com.estebanmm13.pytra_api.error.InvalidTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final TokenGenerator tokenGenerator;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Override
    @Transactional
    public RegisterResponseDto register(RegisterRequestDto registerRequestDto) {

        String normalizedUsername = registerRequestDto.getUsername().toLowerCase();
        String normalizedEmail = registerRequestDto.getEmail().toLowerCase();

        if (userRepository.existsByUsername(normalizedUsername)) {
            log.warn("Registration attempt with existing username: {}", registerRequestDto.getUsername());
            throw new DuplicateResourceException("Username: " + registerRequestDto.getUsername() + " already exists");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration attempt with existing email: {}", registerRequestDto.getEmail());
            throw new DuplicateResourceException("Email: " + registerRequestDto.getEmail() + " already exists");
        }

        var user = User.builder()
                .username(normalizedUsername)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(registerRequestDto.getPassword()))
                .usernameDisplay(registerRequestDto.getUsername())
                .emailVerified(false)
                .googleId(null)
                .createdAt(LocalDateTime.now())
                .role(Role.USER)
                .build();

        User savedUser = userRepository.save(user);

        String rawToken = tokenGenerator.generateTokenRaw();
        String tokenHash = tokenGenerator.hashToken(rawToken);

        var emailVerificationToken = EmailVerificationToken.builder()
                .user(savedUser)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        emailVerificationTokenRepository.save(emailVerificationToken);
        log.info("/verify-email?token= {}", rawToken);

        log.info("User registered with id: {}", savedUser.getId());
        return userMapper.toRegisterResponseDto(savedUser);
    }

    @Override
    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        String normalizedIdentifier = loginRequestDto.getIdentifier().toLowerCase();

        Optional<User> user = userRepository.findByIdentifier(normalizedIdentifier);

        if (user.isEmpty()) {
            throw new InvalidCredentialException("Invalid credentials");
        }
        if (user.get().getPasswordHash() == null) {
            throw new InvalidCredentialException("Invalid credentials");
        }
        if (!passwordEncoder.matches(loginRequestDto.getPassword(), user.get().getPasswordHash())) {
            throw new InvalidCredentialException("Invalid credentials");
        }

        if (user.get().getEmailVerified() == false) {
            throw new EmailNotVerifiedException("Email:" + user.get().getEmail() + " not verified");
        }

        String token = jwtService.generateTokenWithRole(user.get().getUsername(), user.get().getRole().name(), user.get().getId());
        return new LoginResponseDto(token);
    }

    @Override
    @Transactional
    public void verifyEmail(String rawToken) {

        String tokenHash = tokenGenerator.hashToken(rawToken);
        Optional<EmailVerificationToken> emailVerificationToken = emailVerificationTokenRepository.findByTokenHash(tokenHash);

        if (emailVerificationToken.isEmpty()) {
            throw new InvalidTokenException("Invalid token");
        }
        if (emailVerificationToken.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Invalid token");
        }
        if (emailVerificationToken.get().getConsumedAt() != null) {
            throw new InvalidTokenException("Invalid token");
        }

        emailVerificationToken.get().setConsumedAt(LocalDateTime.now());
        emailVerificationTokenRepository.save(emailVerificationToken.get());

        User user = emailVerificationToken.get().getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Override
    public ForgotPasswordResponseDto forgotPassword(ForgotPasswordRequestDto requestDto) {

        String emailNormalized = requestDto.getEmail().toLowerCase();
        Optional<User> user = userRepository.findByEmail(emailNormalized);

        if (user.isEmpty()) {
            return new ForgotPasswordResponseDto();
        }

        String rawToken = tokenGenerator.generateTokenRaw();
        String tokenHash = tokenGenerator.hashToken(rawToken);

        PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                .user(user.get())
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        passwordResetTokenRepository.save(passwordResetToken);
        log.info("/reset-password?token= {}", rawToken);

        return new ForgotPasswordResponseDto();
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDto resetPasswordRequestDto) {

        String tokenHash = tokenGenerator.hashToken(resetPasswordRequestDto.getToken());
        Optional<PasswordResetToken> passwordResetToken = passwordResetTokenRepository.findByTokenHash(tokenHash);

        if (passwordResetToken.isEmpty()) {
            throw new InvalidTokenException("Invalid token");
        }
        if (passwordResetToken.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Invalid token");
        }
        if (passwordResetToken.get().getConsumedAt() != null) {
            throw new InvalidTokenException("Invalid token");
        }
        passwordResetToken.get().setConsumedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(passwordResetToken.get());

        User user = passwordResetToken.get().getUser();
        user.setPasswordHash(passwordEncoder.encode(resetPasswordRequestDto.getNewPassword()));
        userRepository.save(user);

    }


}
