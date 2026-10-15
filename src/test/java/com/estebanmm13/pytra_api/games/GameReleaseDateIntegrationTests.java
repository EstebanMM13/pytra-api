package com.estebanmm13.pytra_api.games;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Unreleased games: a future release date is accepted within sane bounds, and stats keep working. */
class GameReleaseDateIntegrationTests extends AbstractIntegrationTest {

    private String auth;

    @BeforeEach
    void setUp() {
        auth = bearer(createVerifiedUser("release"));
    }

    @Test
    void futureReleaseDateIsAcceptedOnCreateAndUpdate() throws Exception {
        String future = LocalDate.now().plusMonths(6).toString();
        Long id = readId(performAs(auth, post("/api/v1/games"),
                "{\"name\": \"Upcoming\", \"category\": \"SINGLEPLAYER\", \"releaseDate\": \"" + future + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.releaseDate").value(future))
                .andReturn());

        String later = LocalDate.now().plusYears(2).toString();
        performAs(auth, put("/api/v1/games/{id}", id),
                "{\"name\": \"Upcoming\", \"category\": \"ONLINE\", \"releaseDate\": \"" + later + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseDate").value(later));

        // Stats never read the release date; a future one must not break them.
        performAs(auth, get("/api/v1/stats/summary")).andExpect(status().isOk());
        performAs(auth, get("/api/v1/stats/by-year")).andExpect(status().isOk());
        performAs(auth, get("/api/v1/games")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].releaseDate").value(later));
    }

    @Test
    void releaseDateMoreThanTenYearsAheadIsRejectedOnTheReleaseDateField() throws Exception {
        String tooFar = LocalDate.now().plusYears(11).toString();
        performAs(auth, post("/api/v1/games"),
                "{\"name\": \"Vapor\", \"category\": \"SINGLEPLAYER\", \"releaseDate\": \"" + tooFar + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.releaseDate").exists());
    }

    @Test
    void oldReleaseDatesStayValid() throws Exception {
        Long id = readId(performAs(auth, post("/api/v1/games"),
                "{\"name\": \"Ancient\", \"category\": \"SINGLEPLAYER\", \"releaseDate\": \"1900-01-01\"}")
                .andExpect(status().isCreated())
                .andReturn());

        performAs(auth, put("/api/v1/games/{id}", id),
                "{\"name\": \"Ancient 2\", \"category\": \"SINGLEPLAYER\", \"releaseDate\": \"1900-01-01\"}")
                .andExpect(status().isOk());
    }
}
