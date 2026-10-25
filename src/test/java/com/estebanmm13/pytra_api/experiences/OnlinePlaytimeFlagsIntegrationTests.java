package com.estebanmm13.pytra_api.experiences;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Platinum and "100%" on the online playtime (V33): optional, kept when absent, aggregated per game. */
class OnlinePlaytimeFlagsIntegrationTests extends AbstractIntegrationTest {

    private static final String ONLINE = "/api/v1/games/{gameId}/online-playtime";

    private String auth;
    private Long gameId;

    @BeforeEach
    void setUp() throws Exception {
        auth = bearer(createVerifiedUser("onlineflags"));
        gameId = createGameVia(auth, "Arena Legends", "");
    }

    @Test
    void flagsDefaultToFalseOnCreate() throws Exception {
        performAs(auth, put(ONLINE, gameId), "{\"totalHours\": 3.0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platinum").value(false))
                .andExpect(jsonPath("$.fullCompletion").value(false));

        performAs(auth, get("/api/v1/games/{id}", gameId))
                .andExpect(jsonPath("$.hasPlatinum").value(false))
                .andExpect(jsonPath("$.hasFullCompletion").value(false));
    }

    @Test
    void flagsAreStoredIndependentlyKeptWhenAbsentAndCanBeCleared() throws Exception {
        performAs(auth, put(ONLINE, gameId), "{\"totalHours\": 300.0, \"fullCompletion\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullCompletion").value(true))
                .andExpect(jsonPath("$.platinum").value(false));
        performAs(auth, get("/api/v1/games/{id}", gameId))
                .andExpect(jsonPath("$.hasFullCompletion").value(true))
                .andExpect(jsonPath("$.hasPlatinum").value(false));

        // A client that predates the flags saves hours only: nothing is wiped.
        performAs(auth, put(ONLINE, gameId), "{\"totalHours\": 310.0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullCompletion").value(true));

        performAs(auth, put(ONLINE, gameId), "{\"totalHours\": 310.0, \"fullCompletion\": false, \"platinum\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullCompletion").value(false))
                .andExpect(jsonPath("$.platinum").value(true));
        performAs(auth, get(ONLINE, gameId))
                .andExpect(jsonPath("$.platinum").value(true))
                .andExpect(jsonPath("$.fullCompletion").value(false));
        performAs(auth, get("/api/v1/games/{id}", gameId))
                .andExpect(jsonPath("$.hasFullCompletion").value(false))
                .andExpect(jsonPath("$.hasPlatinum").value(true));
    }

    @Test
    void gameWithRunsHasTheFlagWhenOnlyTheOnlineSideHasIt() throws Exception {
        Long hybridId = createGameVia(auth, "Hybrid Shooter", "");
        createExperienceVia(auth, hybridId, run("Campaign", "COMPLETADO", 8, 12.0, 2024, null, null, ""));
        performAs(auth, put(ONLINE, hybridId), "{\"totalHours\": 40.0, \"platinum\": true}")
                .andExpect(status().isOk());

        performAs(auth, get("/api/v1/games/{id}", hybridId))
                .andExpect(jsonPath("$.hasPlatinum").value(true))
                .andExpect(jsonPath("$.hasFullCompletion").value(false));
    }

    @Test
    void summaryCountsOnlineFlagsButYearSummaryDoesNot() throws Exception {
        createExperienceVia(auth, createGameVia(auth, "Story Game", ""),
                run("Main", "COMPLETADO", 9, 20.0, 2024, null, null, ", \"platinum\": true"));
        performAs(auth, put(ONLINE, gameId), "{\"totalHours\": 50.0, \"platinum\": true, \"fullCompletion\": true}")
                .andExpect(status().isOk());

        performAs(auth, get("/api/v1/stats/summary"))
                .andExpect(jsonPath("$.totalPlatinums").value(2))
                .andExpect(jsonPath("$.totalFullCompletions").value(1));
        performAs(auth, get("/api/v1/stats/years/2024"))
                .andExpect(jsonPath("$.platinumCount").value(1))
                .andExpect(jsonPath("$.fullCompletionCount").value(0));
    }
}
