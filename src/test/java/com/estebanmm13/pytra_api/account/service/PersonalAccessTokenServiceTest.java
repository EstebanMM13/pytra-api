package com.estebanmm13.pytra_api.account.service;

import com.estebanmm13.pytra_api.account.dto.CreatedAccessTokenResponseDto;
import com.estebanmm13.pytra_api.account.model.PersonalAccessToken;
import com.estebanmm13.pytra_api.account.repository.PersonalAccessTokenRepository;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.auth.security.TokenGenerator;
import com.estebanmm13.pytra_api.error.ResourceLimitExceededException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonalAccessTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.of(2026, 10, 24, 10, 0);

    private final PersonalAccessTokenRepository tokenRepository = mock(PersonalAccessTokenRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TokenGenerator tokenGenerator = new TokenGenerator();
    private final PersonalAccessTokenService service = new PersonalAccessTokenService(
            tokenRepository, userRepository, tokenGenerator, Clock.fixed(NOW, ZoneOffset.UTC));

    private final User user = User.builder().id(7L).username("alice").build();

    @Test
    void createReturnsThePlaintextOnceAndStoresOnlyItsHash() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(tokenRepository.countByUser_Id(7L)).thenReturn(0L);
        when(tokenRepository.save(any())).thenAnswer(invocation -> {
            PersonalAccessToken token = invocation.getArgument(0);
            token.setId(40L);
            return token;
        });

        CreatedAccessTokenResponseDto created = service.create(7L, "  Obsidian  ");

        assertThat(created.getId()).isEqualTo(40L);
        assertThat(created.getName()).isEqualTo("Obsidian");
        assertThat(created.getCreatedAt()).isEqualTo(LOCAL_NOW);
        // pytra_pat_ + 32 bytes in URL-safe Base64 without padding (43 chars).
        assertThat(created.getToken()).matches("pytra_pat_[A-Za-z0-9_-]{43}");

        ArgumentCaptor<PersonalAccessToken> saved = ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(tokenGenerator.hashToken(created.getToken()))
                .doesNotContain(created.getToken().substring(10));
        assertThat(saved.getValue().getPrefix()).isEqualTo(created.getToken().substring(0, 12));
        assertThat(saved.getValue().getUser()).isSameAs(user);
    }

    @Test
    void createRefusesASixthToken() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(tokenRepository.countByUser_Id(7L)).thenReturn(5L);

        assertThatThrownBy(() -> service.create(7L, "One more"))
                .isInstanceOf(ResourceLimitExceededException.class)
                .hasMessage(ResourceLimitExceededException.ACCESS_TOKEN_LIMIT);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void revokingAnotherUsersTokenIsNotFound() {
        when(tokenRepository.findByIdAndUser_Id(40L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(7L, 40L)).isInstanceOf(ResourceNotFoundException.class);
        verify(tokenRepository, never()).delete(any());
    }

    @Test
    void authenticateIgnoresValuesThatAreNotTokens() {
        assertThat(service.authenticate("eyJhbGciOiJIUzI1NiJ9.e30.sig")).isEmpty();
        assertThat(service.authenticate(null)).isEmpty();
        verify(tokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void authenticateUnknownTokenIsEmpty() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(service.authenticate("pytra_pat_unknown")).isEmpty();
        verify(tokenRepository, never()).touchLastUsedAt(anyLong(), any(), any());
    }

    @Test
    void authenticateStampsLastUseAtMostOncePerMinute() {
        String raw = "pytra_pat_" + "a".repeat(43);
        PersonalAccessToken fresh = token(LOCAL_NOW.minusSeconds(30));
        when(tokenRepository.findByTokenHash(tokenGenerator.hashToken(raw))).thenReturn(Optional.of(fresh));

        assertThat(service.authenticate(raw)).contains(fresh);
        verify(tokenRepository, never()).touchLastUsedAt(anyLong(), any(), any());

        PersonalAccessToken stale = token(LOCAL_NOW.minusMinutes(5));
        when(tokenRepository.findByTokenHash(tokenGenerator.hashToken(raw))).thenReturn(Optional.of(stale));

        assertThat(service.authenticate(raw)).contains(stale);
        verify(tokenRepository).touchLastUsedAt(40L, LOCAL_NOW, LOCAL_NOW.minusMinutes(1));
    }

    @Test
    void authenticateStampsANeverUsedToken() {
        String raw = "pytra_pat_" + "b".repeat(43);
        when(tokenRepository.findByTokenHash(tokenGenerator.hashToken(raw))).thenReturn(Optional.of(token(null)));

        service.authenticate(raw);

        verify(tokenRepository).touchLastUsedAt(40L, LOCAL_NOW, LOCAL_NOW.minusMinutes(1));
    }

    private PersonalAccessToken token(LocalDateTime lastUsedAt) {
        return PersonalAccessToken.builder().id(40L).user(user).name("Obsidian").lastUsedAt(lastUsedAt).build();
    }
}
