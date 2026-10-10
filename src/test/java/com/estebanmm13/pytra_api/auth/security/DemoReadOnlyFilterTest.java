package com.estebanmm13.pytra_api.auth.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoReadOnlyFilterTest {

    private final DemoReadOnlyFilter filter = new DemoReadOnlyFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(boolean demo, String method, String uri) throws Exception {
        return run(demo, method, uri, uri);
    }

    /** {@code servletPath} is what Tomcat derives from the raw {@code requestUri}: decoded, normalized, no ";" params. */
    private MockHttpServletResponse run(boolean demo, String method, String requestUri, String servletPath)
            throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser("demo", 5L, null, demo), null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
        request.setServletPath(servletPath);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // The chain only continues when the request was let through.
        assertThat(chain.getRequest() != null).isEqualTo(response.getStatus() != 403);
        return response;
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/games",
            "PUT, /api/v1/games/1",
            "PATCH, /api/v1/users/me",
            "DELETE, /api/v1/users/me",
            "POST, /api/v1/integrations/steam/sync",
            "POST, /api/v1/integrations/steam/connect-token",
            "DELETE, /api/v1/integrations/steam/link",
            "PUT, /api/v1/stats/years/2024/note",
            "GET, /api/v1/users/me/export",
            "GET, /api/v1/users/me/export/",
            "GET, /api/v1/users/me/export/archive.zip",
            "POST, /api/v1/auth/unknown",
            "POST, /API/V1/AUTH/LOGIN",
            "POST, /api/v1/auth/login/"
    })
    void demoSessionCannotWrite(String method, String uri) throws Exception {
        MockHttpServletResponse response = run(true, method, uri);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"message\":\"DEMO_READ_ONLY\"");
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/games",
            "GET, /api/v1/stats/summary",
            "HEAD, /api/v1/games",
            "OPTIONS, /api/v1/games",
            "POST, /api/v1/auth/demo",
            "POST, /api/v1/auth/login"
    })
    void demoSessionCanReadAndReachPublicAuthEndpoints(String method, String uri) throws Exception {
        assertThat(run(true, method, uri).getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/games",
            "DELETE, /api/v1/users/me",
            "GET, /api/v1/users/me/export"
    })
    void normalSessionIsUnaffected(String method, String uri) throws Exception {
        assertThat(run(false, method, uri).getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @CsvSource({
            // Raw URI starts with the auth prefix, but the path actually routed is a write elsewhere.
            "POST, /api/v1/auth/../games, /api/v1/games",
            "POST, /api/v1/auth/login/../../games, /api/v1/games",
            "POST, /api/v1/auth;x=1/../games, /api/v1/games",
            "DELETE, /api/v1/auth/%2e%2e/users/me, /api/v1/users/me",
            // Raw URI does not look like the export, but it is.
            "GET, /api/v1/users/me/%65xport, /api/v1/users/me/export",
            "GET, /api/v1/users/me/export;jsessionid=1, /api/v1/users/me/export",
            "GET, /api/v1/users/me/./export, /api/v1/users/me/export"
    })
    void demoSessionDecidesOnTheNormalizedPathNotTheRawUri(String method, String requestUri, String servletPath)
            throws Exception {
        assertThat(run(true, method, requestUri, servletPath).getStatus()).isEqualTo(403);
    }
}
