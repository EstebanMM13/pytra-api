package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Anything but a valid, unrevoked session JWT leaves the request anonymous and the chain running (401 comes later). */
class JwtFilterEdgeCasesTest {

    private final JwtService jwtService = new JwtService();
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FilterChain chain = mock(FilterChain.class);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        ReflectionTestUtils.setField(jwtService, "SECRET_KEY",
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        ReflectionTestUtils.setField(jwtService, "EXPIRATION_TIME", 60_000L);
        when(userRepository.isTokenStillValid(anyLong(), any())).thenReturn(true);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private boolean authenticates(String authorizationHeader, DemoMode demoMode) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new JwtFilter(jwtService, userRepository, demoMode).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    @Test
    void validSessionTokenAuthenticates() throws Exception {
        assertThat(authenticates("Bearer " + jwtService.generateTokenWithRole("owner", "USER", 5L), new DemoMode(""))).isTrue();
    }

    @Test
    void missingOrNonBearerHeaderStaysAnonymous() throws Exception {
        assertThat(authenticates(null, new DemoMode(""))).isFalse();
        assertThat(authenticates("Basic b3duZXI6cGFzcw==", new DemoMode(""))).isFalse();
    }

    @Test
    void garbageOrTamperedTokenStaysAnonymous() throws Exception {
        assertThat(authenticates("Bearer not-a-jwt", new DemoMode(""))).isFalse();
        String token = jwtService.generateTokenWithRole("owner", "USER", 5L);
        assertThat(authenticates("Bearer " + token.substring(0, token.length() - 2) + "xx", new DemoMode(""))).isFalse();
    }

    @Test
    void expiredTokenStaysAnonymous() throws Exception {
        ReflectionTestUtils.setField(jwtService, "EXPIRATION_TIME", -1_000L);
        assertThat(authenticates("Bearer " + jwtService.generateTokenWithRole("owner", "USER", 5L), new DemoMode(""))).isFalse();
    }

    @Test
    void demoTokenStopsWorkingOnceDemoModeIsOffOrMoved() throws Exception {
        String demoToken = "Bearer " + jwtService.generateDemoToken("demo", 7L);

        assertThat(authenticates(demoToken, new DemoMode("7"))).isTrue();
        SecurityContextHolder.clearContext();
        assertThat(authenticates(demoToken, new DemoMode(""))).isFalse();
        assertThat(authenticates(demoToken, new DemoMode("8"))).isFalse();
    }
}
