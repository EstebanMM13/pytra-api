package com.estebanmm13.pytra_api.games;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Pendiente" is a flag of the game (backlog), not a run status. */
class GameBacklogIntegrationTests extends AbstractIntegrationTest {

    private String auth;

    @BeforeEach
    void setUp() throws Exception {
        auth = bearer(createVerifiedUser("backlog"));
    }

    @Test
    void gameKeepsItsBacklogFlagAndOlderClientsWithoutItDoNotClearIt() throws Exception {
        Long gameId = createGameVia(auth, "Silksong", ", \"backlog\": true");
        Long other = createGameVia(auth, "Hades", "");

        performAs(auth, get("/api/v1/games/{id}", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backlog").value(true));
        performAs(auth, get("/api/v1/games/{id}", other))
                .andExpect(jsonPath("$.backlog").value(false));

        // A client built before the flag sends no value: the game stays in the backlog.
        performAs(auth, put("/api/v1/games/{id}", gameId), "{\"name\": \"Silksong\", \"category\": \"SINGLEPLAYER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backlog").value(true));
        performAs(auth, put("/api/v1/games/{id}", gameId),
                "{\"name\": \"Silksong\", \"category\": \"SINGLEPLAYER\", \"backlog\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backlog").value(false));
    }

    @Test
    void startingARunTakesTheGameOutOfTheBacklog() throws Exception {
        Long gameId = createGameVia(auth, "Silksong", ", \"backlog\": true");

        createExperienceVia(auth, gameId, run("First", "EN_CURSO", null, 1.0, null, null, null, ""));

        performAs(auth, get("/api/v1/games/{id}", gameId))
                .andExpect(jsonPath("$.backlog").value(false));
    }

    @Test
    void runsCanNoLongerBePending() throws Exception {
        Long gameId = createGameVia(auth, "Silksong", "");

        performAs(auth, post("/api/v1/games/{gameId}/experiences", gameId),
                run("Someday", "PENDIENTE", null, 0.0, null, null, null, ""))
                .andExpect(status().isBadRequest());
    }
}
