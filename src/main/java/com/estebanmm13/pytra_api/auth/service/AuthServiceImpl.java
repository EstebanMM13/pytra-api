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
import com.estebanmm13.pytra_api.auth.mapper.UserMapper;
import com.estebanmm13.pytra_api.auth.model.*;
import com.estebanmm13.pytra_api.auth.repository.EmailVerificationTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.ExchangeCodeTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.PasswordResetTokenRepository;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.auth.security.DemoMode;
import com.estebanmm13.pytra_api.auth.security.JwtService;
import com.estebanmm13.pytra_api.auth.security.TokenGenerator;
import com.estebanmm13.pytra_api.auth.validation.RegistrationPolicy;
import com.estebanmm13.pytra_api.auth.validation.UsernamePolicy;
import com.estebanmm13.pytra_api.error.RegistrationClosedException;
import jakarta.annotation.PostConstruct;
import com.estebanmm13.pytra_api.error.DuplicateResourceException;
import com.estebanmm13.pytra_api.error.EmailNotVerifiedException;
import com.estebanmm13.pytra_api.error.ExpiredTokenException;
import com.estebanmm13.pytra_api.error.InvalidCredentialException;
import com.estebanmm13.pytra_api.error.InvalidTokenException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    static final Duration EMAIL_VERIFICATION_TOKEN_TTL = Duration.ofHours(24);
    static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofHours(1);
    // Minimum time between two emails of the same kind for one account (basic abuse protection).
    static final Duration EMAIL_RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final TokenGenerator tokenGenerator;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ExchangeCodeTokenRepository  exchangeCodeTokenRepository;
    private final AuthEmailService authEmailService;
    private final RegistrationPolicy registrationPolicy;
    private final DemoMode demoMode;

    // Hash compared against when the account does not exist (or has no password), so a failed login costs
    // the same bcrypt work either way and response time does not reveal which identifiers are registered.
    private String dummyPasswordHash;

    @PostConstruct
    void initDummyPasswordHash() {
        dummyPasswordHash = passwordEncoder.encode(tokenGenerator.generateTokenRaw());
    }

    @Override
    @Transactional
    public RegisterResponseDto register(RegisterRequestDto registerRequestDto) {

        String normalizedUsername = UsernamePolicy.normalize(registerRequestDto.getUsername());
        String normalizedEmail = UsernamePolicy.normalizeEmail(registerRequestDto.getEmail());

        // Checked first: nothing is created or sent, and uninvited callers learn nothing about existing accounts.
        if (!registrationPolicy.isAllowed(normalizedEmail)) {
            log.warn("Registration rejected: email not on the invite allowlist");
            throw new RegistrationClosedException();
        }

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

        User savedUser;
        try {
            // Flush now so a concurrent registration hitting the unique constraints surfaces here as a 409.
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("Registration lost a race on username/email uniqueness");
            throw new DuplicateResourceException("Username or email already exists");
        }
        issueVerificationEmail(savedUser);

        log.info("User registered with id: {}", savedUser.getId());
        return userMapper.toRegisterResponseDto(savedUser);
    }

    @Override
    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        String normalizedIdentifier = UsernamePolicy.normalizeEmail(loginRequestDto.getIdentifier());

        // Usernames can never contain '@', so the identifier type is unambiguous and the lookup returns
        // at most one row (an "username = email OR email = email" query could match two accounts).
        Optional<User> user = normalizedIdentifier.contains("@")
                ? userRepository.findByEmail(normalizedIdentifier)
                : userRepository.findByUsername(normalizedIdentifier);

        if (user.isEmpty() || user.get().getPasswordHash() == null) {
            passwordEncoder.matches(loginRequestDto.getPassword(), dummyPasswordHash);
            throw new InvalidCredentialException("Invalid credentials");
        }
        if (!passwordEncoder.matches(loginRequestDto.getPassword(), user.get().getPasswordHash())) {
            throw new InvalidCredentialException("Invalid credentials");
        }

        // Checked only after the password matched, so it does not reveal which emails are registered.
        if (!user.get().getEmailVerified()) {
            throw new EmailNotVerifiedException("Email not verified");
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
        if (emailVerificationToken.get().getConsumedAt() != null) {
            // Opening the same link again after a successful verification is not an error for the user.
            if (emailVerificationToken.get().getUser().getEmailVerified()) {
                return;
            }
            throw new InvalidTokenException("Invalid token");
        }
        if (emailVerificationToken.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ExpiredTokenException("Token expired");
        }

        emailVerificationToken.get().setConsumedAt(LocalDateTime.now());
        emailVerificationTokenRepository.save(emailVerificationToken.get());

        User user = emailVerificationToken.get().getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public ResendVerificationResponseDto resendVerification(ResendVerificationRequestDto requestDto) {

        // Always the same response: it must not reveal whether an account exists or is verified.
        String emailNormalized = UsernamePolicy.normalizeEmail(requestDto.getEmail());
        Optional<User> user = userRepository.findByEmailForUpdate(emailNormalized);

        if (user.isEmpty() || user.get().getEmailVerified()) {
            return new ResendVerificationResponseDto();
        }

        Optional<EmailVerificationToken> lastToken =
                emailVerificationTokenRepository.findFirstByUser_IdOrderByExpiresAtDesc(user.get().getId());
        if (lastToken.isPresent() && issuedRecently(lastToken.get().getExpiresAt(), EMAIL_VERIFICATION_TOKEN_TTL)) {
            log.warn("Verification email resend throttled for user id: {}", user.get().getId());
            return new ResendVerificationResponseDto();
        }

        // Only the newest link stays valid.
        LocalDateTime now = LocalDateTime.now();
        emailVerificationTokenRepository.findAllByUser_IdAndConsumedAtIsNull(user.get().getId())
                .forEach(token -> token.setConsumedAt(now));

        issueVerificationEmail(user.get());
        return new ResendVerificationResponseDto();
    }

    @Override
    @Transactional
    public ForgotPasswordResponseDto forgotPassword(ForgotPasswordRequestDto requestDto) {

        String emailNormalized = UsernamePolicy.normalizeEmail(requestDto.getEmail());
        Optional<User> user = userRepository.findByEmailForUpdate(emailNormalized);

        if (user.isEmpty()) {
            return new ForgotPasswordResponseDto();
        }

        Optional<PasswordResetToken> lastToken =
                passwordResetTokenRepository.findFirstByUser_IdOrderByExpiresAtDesc(user.get().getId());
        if (lastToken.isPresent() && issuedRecently(lastToken.get().getExpiresAt(), PASSWORD_RESET_TOKEN_TTL)) {
            log.warn("Password reset email throttled for user id: {}", user.get().getId());
            return new ForgotPasswordResponseDto();
        }

        // Only the newest link stays valid.
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.findAllByUser_IdAndConsumedAtIsNull(user.get().getId())
                .forEach(token -> token.setConsumedAt(now));

        String rawToken = tokenGenerator.generateTokenRaw();
        String tokenHash = tokenGenerator.hashToken(rawToken);

        PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                .user(user.get())
                .tokenHash(tokenHash)
                .expiresAt(now.plus(PASSWORD_RESET_TOKEN_TTL))
                .build();

        passwordResetTokenRepository.save(passwordResetToken);
        authEmailService.sendPasswordResetEmail(
                user.get().getEmail(), user.get().getUsernameDisplay(), rawToken, PASSWORD_RESET_TOKEN_TTL);

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
        if (passwordResetToken.get().getConsumedAt() != null) {
            throw new InvalidTokenException("Invalid token");
        }
        if (passwordResetToken.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ExpiredTokenException("Token expired");
        }
        passwordResetToken.get().setConsumedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(passwordResetToken.get());

        User user = passwordResetToken.get().getUser();
        user.setPasswordHash(passwordEncoder.encode(resetPasswordRequestDto.getNewPassword()));
        // The reset link was delivered to this inbox, which proves ownership of the email.
        user.setEmailVerified(true);
        userRepository.save(user);

    }

    @Override
    public LoginResponseDto exchangeCodeToken(ExchangeCodeTokenRequestDto exchangeCodeTokenRequestDto) {

        String codeHash = tokenGenerator.hashToken(exchangeCodeTokenRequestDto.getCode());
        Optional<ExchangeCodeToken> exchangeCodeTokenRequest = exchangeCodeTokenRepository.findByTokenHash(codeHash);

        if (exchangeCodeTokenRequest.isEmpty()) {
            throw new InvalidTokenException("Invalid token");
        }
        if (exchangeCodeTokenRequest.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Invalid token");
        }
        if (exchangeCodeTokenRequest.get().getConsumedAt() != null) {
            throw new InvalidTokenException("Invalid token");
        }

        exchangeCodeTokenRequest.get().setConsumedAt(LocalDateTime.now());
        exchangeCodeTokenRepository.save(exchangeCodeTokenRequest.get());

        User user = exchangeCodeTokenRequest.get().getUser();
        String token = jwtService.generateTokenWithRole(user.getUsername(), user.getRole().name(), user.getId());
        return new LoginResponseDto(token);

    }

    /**
     * No refresh token exists in this API, and the demo token is a separate short-lived JWT carrying the
     * demo claim, so a demo session can never turn into a normal one: when it expires the client asks again.
     */
    @Override
    @Transactional(readOnly = true)
    public LoginResponseDto demoLogin() {
        if (!demoMode.isEnabled()) {
            throw new ResourceNotFoundException("Demo not available");
        }
        User user = userRepository.findById(demoMode.getDemoUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Demo not available"));
        return new LoginResponseDto(jwtService.generateDemoToken(user.getUsername(), user.getId()));
    }

    private void issueVerificationEmail(User user) {
        String rawToken = tokenGenerator.generateTokenRaw();

        var emailVerificationToken = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenGenerator.hashToken(rawToken))
                .expiresAt(LocalDateTime.now().plus(EMAIL_VERIFICATION_TOKEN_TTL))
                .build();

        emailVerificationTokenRepository.save(emailVerificationToken);
        authEmailService.sendVerificationEmail(
                user.getEmail(), user.getUsernameDisplay(), rawToken, EMAIL_VERIFICATION_TOKEN_TTL);
    }

    // Token tables have no created_at column: the issue time is derived from expiresAt and the fixed TTL.
    private static boolean issuedRecently(LocalDateTime expiresAt, Duration ttl) {
        LocalDateTime issuedAt = expiresAt.minus(ttl);
        return issuedAt.isAfter(LocalDateTime.now().minus(EMAIL_RESEND_COOLDOWN));
    }
}
