package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.account.model.PersonalAccessToken;
import com.estebanmm13.pytra_api.account.service.PersonalAccessTokenService;
import com.estebanmm13.pytra_api.auth.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonalAccessTokenFilterTest {

    private static final String RAW = "pytra_pat_" + "x".repeat(43);

    private final PersonalAccessTokenService tokenService = mock(PersonalAccessTokenService.class);
    private final PersonalAccessTokenFilter filter = new PersonalAccessTokenFilter(tokenService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(String method, String servletPath, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, servletPath);
        request.setServletPath(servletPath);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void validTokenAuthenticatesTheObsidianExport() throws Exception {
        User owner = User.builder().id(7L).username("alice").build();
        when(tokenService.authenticate(RAW)).thenReturn(Optional.of(
                PersonalAccessToken.builder().id(40L).user(owner).build()));

        Authentication authentication = run("GET", "/api/v1/export/obsidian", "Bearer " + RAW);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOfSatisfying(PersonalAccessTokenPrincipal.class, p -> {
            assertThat(p.getUserId()).isEqualTo(7L);
            assertThat(p.getTokenId()).isEqualTo(40L);
            assertThat(p.isDemo()).isFalse();
        });
        // No role: the token can never pass an ADMIN/USER role check.
        assertThat(authentication.getAuthorities()).extracting("authority")
                .containsExactly(PersonalAccessTokenFilter.TOKEN_AUTHORITY);
    }

    @Test
    void unknownOrRevokedTokenStaysAnonymous() throws Exception {
        when(tokenService.authenticate(RAW)).thenReturn(Optional.empty());

        assertThat(run("GET", "/api/v1/export/obsidian", "Bearer " + RAW)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/games",
            "GET, /api/v1/users/me",
            "GET, /api/v1/users/me/export",
            "GET, /api/v1/users/me/tokens",
            "POST, /api/v1/users/me/tokens",
            "DELETE, /api/v1/users/me",
            "POST, /api/v1/export/obsidian",
            "GET, /api/v1/export/obsidian/",
            "GET, /api/v1/export/obsidianx",
    })
    void tokenIsIgnoredEverywhereElse(String method, String path) throws Exception {
        assertThat(run(method, path, "Bearer " + RAW)).isNull();
        verify(tokenService, never()).authenticate(any());
    }

    @Test
    void jwtValuesAreLeftToJwtFilter() throws Exception {
        assertThat(run("GET", "/api/v1/export/obsidian", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.sig")).isNull();
        assertThat(run("GET", "/api/v1/export/obsidian", null)).isNull();
        verify(tokenService, never()).authenticate(any());
    }
}
