package com.estebanmm13.pytra_api.games;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Per-game aggregates the redesigned library table reads from GET /games. */
class GameLibraryAggregatesTests extends AbstractIntegrationTest {

    private String auth;
    private Long playedId;
    private Long untouchedId;

    @BeforeEach
    void createLibrary() throws Exception {
        auth = bearer(createVerifiedUser("library"));
        playedId = createGameVia(auth, "Played", "");
        untouchedId = createGameVia(auth, "Untouched", "");

        createExperienceVia(auth, playedId,
                run("First", "COMPLETADO", 7, 10.0, 2022, null, "2022-05-01", ", \"platinum\": true"));
        createExperienceVia(auth, playedId,
                run("Replay", "EN_CURSO", null, 3.5, null, "2024-01-01", null, ""));
    }

    @Test
    void listIncludesAggregatesForEveryGame() throws Exception {
        performAs(auth, get("/api/v1/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(playedId))
                .andExpect(jsonPath("$[0].experienceCount").value(2))
                .andExpect(jsonPath("$[0].totalHours").value(13.5))
                .andExpect(jsonPath("$[0].bestRating").value(7))
                .andExpect(jsonPath("$[0].lastExperienceStatus").value("EN_CURSO"))
                .andExpect(jsonPath("$[0].lastPlayedYear").value(2024))
                .andExpect(jsonPath("$[0].hasPlatinum").value(true))
                .andExpect(jsonPath("$[1].id").value(untouchedId))
                .andExpect(jsonPath("$[1].experienceCount").value(0))
                .andExpect(jsonPath("$[1].totalHours").value(0.0))
                .andExpect(jsonPath("$[1].bestRating").value(nullValue()))
                .andExpect(jsonPath("$[1].lastExperienceStatus").value(nullValue()))
                .andExpect(jsonPath("$[1].lastPlayedYear").value(nullValue()))
                .andExpect(jsonPath("$[1].hasPlatinum").value(false));
    }

    @Test
    void getByIdIncludesTheSameAggregates() throws Exception {
        performAs(auth, get("/api/v1/games/{id}", playedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Played"))
                .andExpect(jsonPath("$.experienceCount").value(2))
                .andExpect(jsonPath("$.lastExperienceStatus").value("EN_CURSO"));
    }

    @Test
    void aggregatesNeverIncludeOtherUsersExperiences() throws Exception {
        String otherAuth = bearer(createVerifiedUser("libother"));
        Long otherGame = createGameVia(otherAuth, "Played", "");
        createExperienceVia(otherAuth, otherGame, run("Other", "COMPLETADO", 10, 99.0, 2025, null, null, ", \"platinum\": true"));

        performAs(auth, get("/api/v1/games/{id}", playedId))
                .andExpect(jsonPath("$.totalHours").value(13.5))
                .andExpect(jsonPath("$.bestRating").value(7));
        performAs(otherAuth, get("/api/v1/games"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].experienceCount").value(1))
                .andExpect(jsonPath("$[0].totalHours").value(99.0));
    }

    @Test
    void platformsAndLastPlayedAtComeFromTheGamesExperiences() throws Exception {
        createExperienceVia(auth, playedId, """
                {"runLabel": "Handheld", "status": "COMPLETADO", "platform": "SWITCH", "endDate": "2023-08-15"}""");

        performAs(auth, get("/api/v1/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(playedId))
                // Distinct, in Platform enum order (PC before SWITCH), whatever the insertion order.
                .andExpect(jsonPath("$[0].platforms", contains("PC", "SWITCH")))
                // Latest of every endDate/startDate: the replay's startDate.
                .andExpect(jsonPath("$[0].lastPlayedAt").value("2024-01-01"))
                .andExpect(jsonPath("$[1].id").value(untouchedId))
                .andExpect(jsonPath("$[1].platforms", empty()))
                .andExpect(jsonPath("$[1].lastPlayedAt").value(nullValue()));

        performAs(auth, get("/api/v1/games/{id}", playedId))
                .andExpect(jsonPath("$.platforms", contains("PC", "SWITCH")))
                .andExpect(jsonPath("$.lastPlayedAt").value("2024-01-01"));
    }

    @Test
    void platformsAndLastPlayedAtNeverIncludeOtherUsersExperiences() throws Exception {
        String otherAuth = bearer(createVerifiedUser("libplatforms"));
        Long otherGame = createGameVia(otherAuth, "Played", "");
        createExperienceVia(otherAuth, otherGame, """
                {"runLabel": "Other", "status": "EN_CURSO", "platform": "PS5", "startDate": "2025-02-02"}""");

        performAs(auth, get("/api/v1/games/{id}", playedId))
                .andExpect(jsonPath("$.platforms", contains("PC")))
                .andExpect(jsonPath("$.lastPlayedAt").value("2024-01-01"));
        performAs(otherAuth, get("/api/v1/games"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].platforms", contains("PS5")))
                .andExpect(jsonPath("$[0].lastPlayedAt").value("2025-02-02"));
    }
}
