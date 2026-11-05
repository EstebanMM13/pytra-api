package com.estebanmm13.pytra_api.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Filters answer with the same ApiError JSON controllers produce, properly escaped. */
class ApiErrorResponsesTest {

    @Test
    void writesTheApiErrorShape() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiErrorResponses.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too \"many\" requests",
                LocalDateTime.of(2026, 11, 5, 18, 30, 0));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).isEqualTo(
                "{\"status\":429,\"message\":\"Too \\\"many\\\" requests\",\"fieldErrors\":{},\"timestamp\":\"2026-11-05T18:30:00Z\"}");
    }
}
