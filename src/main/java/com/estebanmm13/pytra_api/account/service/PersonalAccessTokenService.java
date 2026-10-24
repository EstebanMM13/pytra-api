package com.estebanmm13.pytra_api.account.service;

import com.estebanmm13.pytra_api.account.dto.AccessTokenResponseDto;
import com.estebanmm13.pytra_api.account.dto.CreatedAccessTokenResponseDto;
import com.estebanmm13.pytra_api.account.model.PersonalAccessToken;
import com.estebanmm13.pytra_api.account.repository.PersonalAccessTokenRepository;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.auth.security.TokenGenerator;
import com.estebanmm13.pytra_api.error.ResourceLimitExceededException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Personal access tokens: long-lived, read-only credentials for external tools (the Obsidian sync).
 * The raw token is {@value #TOKEN_PREFIX} + 32 SecureRandom bytes in URL-safe Base64; only its SHA-256
 * is stored, so a lookup by hash is the whole verification.
 */
@Service
public class PersonalAccessTokenService {

    public static final String TOKEN_PREFIX = "pytra_pat_";
    static final int MAX_TOKENS_PER_USER = 5;
    static final int DISPLAY_PREFIX_LENGTH = 12;
    /** {@code last_used_at} is written at most once per window per token. */
    static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(1);

    private final PersonalAccessTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;

    public PersonalAccessTokenService(PersonalAccessTokenRepository tokenRepository,
                                      UserRepository userRepository,
                                      TokenGenerator tokenGenerator) {
        this(tokenRepository, userRepository, tokenGenerator, Clock.systemDefaultZone());
    }

    PersonalAccessTokenService(PersonalAccessTokenRepository tokenRepository,
                               UserRepository userRepository,
                               TokenGenerator tokenGenerator,
                               Clock clock) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    /** True when the bearer value has the shape of a personal access token (not a JWT). */
    public static boolean looksLikeToken(String bearerValue) {
        return bearerValue != null && bearerValue.startsWith(TOKEN_PREFIX);
    }

    @Transactional
    public CreatedAccessTokenResponseDto create(Long userId, String name) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (tokenRepository.countByUser_Id(userId) >= MAX_TOKENS_PER_USER) {
            throw new ResourceLimitExceededException(ResourceLimitExceededException.ACCESS_TOKEN_LIMIT);
        }
        String rawToken = TOKEN_PREFIX + tokenGenerator.generateTokenRaw();
        PersonalAccessToken token = tokenRepository.save(PersonalAccessToken.builder()
                .user(user)
                .name(name.trim())
                .tokenHash(tokenGenerator.hashToken(rawToken))
                .prefix(rawToken.substring(0, DISPLAY_PREFIX_LENGTH))
                .createdAt(LocalDateTime.now(clock))
                .build());
        return new CreatedAccessTokenResponseDto(token.getId(), token.getName(), rawToken, token.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<AccessTokenResponseDto> list(Long userId) {
        return tokenRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(token -> new AccessTokenResponseDto(token.getId(), token.getName(), token.getPrefix(),
                        token.getCreatedAt(), token.getLastUsedAt()))
                .toList();
    }

    /** Revokes (deletes) one of the user's tokens; another user's token is reported as not found. */
    @Transactional
    public void revoke(Long userId, Long tokenId) {
        PersonalAccessToken token = tokenRepository.findByIdAndUser_Id(tokenId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Access token not found"));
        tokenRepository.delete(token);
    }

    /**
     * The live token matching {@code rawToken}, with its owner loaded, and its last use stamped (at most
     * once per {@link #LAST_USED_RESOLUTION}). Empty for an unknown or revoked token.
     */
    public Optional<PersonalAccessToken> authenticate(String rawToken) {
        if (!looksLikeToken(rawToken)) {
            return Optional.empty();
        }
        Optional<PersonalAccessToken> token = tokenRepository.findByTokenHash(tokenGenerator.hashToken(rawToken));
        token.ifPresent(found -> {
            LocalDateTime now = LocalDateTime.now(clock);
            if (found.getLastUsedAt() == null || found.getLastUsedAt().isBefore(now.minus(LAST_USED_RESOLUTION))) {
                tokenRepository.touchLastUsedAt(found.getId(), now, now.minus(LAST_USED_RESOLUTION));
            }
        });
        return token;
    }
}
