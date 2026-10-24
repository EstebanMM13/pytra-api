package com.estebanmm13.pytra_api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

class SecurityConfigCorsTest {

    private static final String ORIGIN = "http://localhost:4200";

    @Test
    void preflightIsCacheableForTwoHours() throws Exception {
        SecurityConfig config = new SecurityConfig(null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins", List.of(ORIGIN));

        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/api/v1/games");
        preflight.addHeader(HttpHeaders.ORIGIN, ORIGIN);
        preflight.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        preflight.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization");
        MockHttpServletResponse response = new MockHttpServletResponse();

        CorsConfiguration cors = config.corsConfigurationSource().getCorsConfiguration(preflight);
        boolean accepted = new DefaultCorsProcessor().processRequest(cors, preflight, response);

        assertThat(accepted).isTrue();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE)).isEqualTo("7200");
    }
}
