package com.estebanmm13.pytra_api.error;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Writes an {@link ApiError} body from a servlet filter, where no controller advice runs. Serialized with
 * Jackson 3 like MVC responses, so filter errors and controller errors share one shape and escaping.
 */
public final class ApiErrorResponses {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ApiErrorResponses() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, String message, LocalDateTime timestamp)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSON.writeValueAsString(new ApiError(status.value(), message, Map.of(), timestamp)));
    }
}
