package com.estebanmm13.pytra_api;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** springdoc is enabled outside the prod profile and its paths are public in SecurityConfig. */
class ApiDocsTests extends AbstractIntegrationTest {

    @Test
    void openApiDocsArePubliclyAvailableOutsideProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/v1/games']").exists());
    }
}
