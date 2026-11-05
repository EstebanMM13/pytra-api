package com.estebanmm13.pytra_api.error;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Framework-level request errors and unexpected failures answer with the same ApiError body as domain errors. */
class GlobalExceptionHandlerShapeTest {

    @ResponseStatus(HttpStatus.I_AM_A_TEAPOT)
    static class TeapotException extends RuntimeException {
    }

    @RestController
    static class ProbeController {
        @PostMapping("/probe/body")
        String body(@RequestBody Map<String, Object> body) {
            return "ok";
        }

        @GetMapping("/probe/param")
        String param(@RequestParam int year) {
            return "ok";
        }

        @GetMapping("/probe/day/{date}")
        String day(@PathVariable LocalDate date) {
            return "ok";
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("SELECT secret FROM internals");
        }

        @GetMapping("/probe/teapot")
        String teapot() {
            throw new TeapotException();
        }

        @GetMapping("/probe/denied")
        String denied() {
            throw new AccessDeniedException("nope");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    private static void assertApiError(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedJsonBodyIsA400ApiError() throws Exception {
        ResultActions result = mockMvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json"));
        assertApiError(result, 400);
        result.andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void missingRequestParameterIsA400ApiErrorNamingIt() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/param"));
        assertApiError(result, 400);
        result.andExpect(jsonPath("$.message").value("Missing parameter"))
                .andExpect(jsonPath("$.fieldErrors.year").value("Required"));
    }

    @Test
    void wronglyTypedParametersAreA400ApiErrorNamingThem() throws Exception {
        ResultActions query = mockMvc.perform(get("/probe/param").param("year", "abc"));
        assertApiError(query, 400);
        query.andExpect(jsonPath("$.message").value("Invalid parameter"))
                .andExpect(jsonPath("$.fieldErrors.year").value("Invalid value"));

        ResultActions path = mockMvc.perform(get("/probe/day/{date}", "20-10-2026"));
        assertApiError(path, 400);
        path.andExpect(jsonPath("$.fieldErrors.date").value("Invalid value"));
    }

    @Test
    void otherFrameworkErrorsKeepTheirStatusInTheApiErrorShape() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/body"));
        assertApiError(result, 405);
    }

    @Test
    void unexpectedFailureIsA500ApiErrorThatHidesTheDetails() throws Exception {
        ResultActions result = mockMvc.perform(get("/probe/boom"));
        assertApiError(result, 500);
        result.andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void responseStatusExceptionsKeepTheirAnnotatedStatus() throws Exception {
        mockMvc.perform(get("/probe/teapot")).andExpect(status().is(418));
    }

    @Test
    void securityExceptionsAreLeftToSpringSecurity() {
        assertThatThrownBy(() -> mockMvc.perform(get("/probe/denied")))
                .isInstanceOf(ServletException.class)
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }
}
