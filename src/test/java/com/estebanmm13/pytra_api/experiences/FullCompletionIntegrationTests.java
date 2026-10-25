package com.estebanmm13.pytra_api.experiences;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The per-run "100%" flag: optional in requests, independent of platinum, aggregated per game. */
class FullCompletionIntegrationTests extends AbstractIntegrationTest {

    private String auth;
    private Long gameId;

    @BeforeEach
    void setUp() throws Exception {
        auth = bearer(createVerifiedUser("fullcompletion"));
        gameId = createGameVia(auth, "Completion Game", "");
    }

    @Test
    void requestWithoutTheFlagDefaultsToFalse() throws Exception {
        performAs(auth, post("/api/v1/games/{gameId}/experiences", gameId),
                run("Old client", "COMPLETADO", 8, 10.0, null, null, null, ", \"platinum\": true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.platinum").value(true))
                .andExpect(jsonPath("$.fullCompletion").value(false));

        performAs(auth, get("/api/v1/games/{id}", gameId))
                .andExpect(jsonPath("$.hasPlatinum").value(true))
                .andExpect(jsonPath("$.hasFullCompletion").value(false));
    }

    @Test
    void flagIsStoredIndependentlyOfPlatinumAndCanBeCleared() throws Exception {
        Long runId = createExperienceVia(auth, gameId,
                run("Hundred", "COMPLETADO", 9, 50.0, null, null, null, ", \"fullCompletion\": true"));

        performAs(auth, get("/api/v1/experiences/{id}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullCompletion").value(true))
                .andExpect(jsonPath("$.platinum").value(false));
        performAs(auth, get("/api/v1/games/{id}", gameId))
                .andExpect(jsonPath("$.hasFullCompletion").value(true))
                .andExpect(jsonPath("$.hasPlatinum").value(false));

        performAs(auth, put("/api/v1/experiences/{id}", runId),
                run("Hundred", "COMPLETADO", 9, 50.0, null, null, null, ", \"fullCompletion\": false, \"platinum\": true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullCompletion").value(false))
                .andExpect(jsonPath("$.platinum").value(true));
        performAs(auth, get("/api/v1/games/{id}", gameId))
                .andExpect(jsonPath("$.hasFullCompletion").value(false));
    }
}
