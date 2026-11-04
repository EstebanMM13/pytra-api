package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A JWT authenticates only while its account exists and it was issued at or after the account's revocation cut-off. */
class JwtFilterRevocationTest {

    private static final String JWT = "header.payload.signature";
    private static final Instant ISSUED_AT = Instant.parse("2026-11-04T10:00:00Z");

    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtFilter filter = new JwtFilter(jwtService, userRepository, new DemoMode(""));

    @BeforeEach
    void validSignature() {
        SecurityContextHolder.clearContext();
        when(jwtService.getUserName(JWT)).thenReturn("owner");
        when(jwtService.getUserId(JWT)).thenReturn(5L);
        when(jwtService.getIssuedAt(JWT)).thenReturn(ISSUED_AT);
        when(jwtService.isDemo(JWT)).thenReturn(false);
        when(jwtService.getAuthorities(JWT)).thenReturn(List.of());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void filter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + JWT);
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
    }

    @Test
    void tokenStillValidAuthenticates() throws Exception {
        when(userRepository.isTokenStillValid(5L, ISSUED_AT)).thenReturn(true);

        filter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(((AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .getUserId()).isEqualTo(5L);
    }

    @Test
    void revokedOrDeletedAccountTokenStaysAnonymous() throws Exception {
        when(userRepository.isTokenStillValid(5L, ISSUED_AT)).thenReturn(false);

        filter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validationIsTheOnlyUserLookup() throws Exception {
        when(userRepository.isTokenStillValid(5L, ISSUED_AT)).thenReturn(true);

        filter();

        verify(userRepository, never()).existsById(anyLong());
    }
}
