package com.estebanmm13.pytra_api.experiences;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.platforms.repository.PlatformRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Online playtime platforms (V23) and how online playtime feeds the library aggregates. */
class OnlinePlaytimePlatformsIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private PlatformRepository platformRepository;

    private String auth;
    private Long gameId;
    private Long pcId;
    private Long ps5Id;
    private Long switchId;

    @BeforeEach
    void setUp() throws Exception {
        auth = bearer(createVerifiedUser("onlineplat"));
        gameId = createGameVia(auth, "Arena", "");
        pcId = platformRepository.findByLegacyCodeIgnoreCase("PC").orElseThrow().getId();
        ps5Id = platformRepository.findByLegacyCodeIgnoreCase("PS5").orElseThrow().getId();
        switchId = platformRepository.findByLegacyCodeIgnoreCase("SWITCH").orElseThrow().getId();
    }

    @Test
    void platformsAreStoredAndReturnedInCatalogOrder() throws Exception {
        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId),
                "{\"totalHours\": 30.0, \"platformIds\": [" + switchId + ", " + pcId + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platforms[*].name", contains("PC", "Switch")))
                .andExpect(jsonPath("$.platforms[0].id").value(pcId))
                .andExpect(jsonPath("$.platforms[0].color").value("#8A857C"));

        performAs(auth, get("/api/v1/games/{gameId}/online-playtime", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platforms[*].name", contains("PC", "Switch")));
    }

    @Test
    void omittedPlatformIdsKeepThePlatformsAndAnEmptyListClearsThem() throws Exception {
        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId),
                "{\"totalHours\": 1.0, \"platformIds\": [" + ps5Id + "]}")
                .andExpect(status().isOk());

        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId), "{\"totalHours\": 2.0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platforms[*].name", contains("PS5")));

        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId),
                "{\"totalHours\": 2.0, \"platformIds\": []}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platforms", empty()));
    }

    @Test
    void unknownPlatformIdIsRejected() throws Exception {
        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId),
                "{\"totalHours\": 1.0, \"platformIds\": [" + pcId + ", 99999999]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_PLATFORM"));
    }

    @Test
    void libraryMergesOnlineHoursRatingAndPlatforms() throws Exception {
        createExperienceVia(auth, gameId, run("Campaign", "COMPLETADO", 7, 10.0, null, null, null, ""));
        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId),
                "{\"totalHours\": 40.5, \"generalRating\": 8.5, \"platformIds\": [" + ps5Id + ", " + pcId + "]}")
                .andExpect(status().isOk());

        performAs(auth, get("/api/v1/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].experienceCount").value(1))
                .andExpect(jsonPath("$[0].totalHours").value(50.5))
                .andExpect(jsonPath("$[0].bestRating").value(8.5))
                // The run is on PC (legacy "PC" code); PS5 comes only from the online record.
                .andExpect(jsonPath("$[0].platformRefs[*].name", contains("PC", "PS5")));

        performAs(auth, get("/api/v1/games/{id}", gameId))
                .andExpect(jsonPath("$.totalHours").value(50.5))
                .andExpect(jsonPath("$.platformRefs[*].name", contains("PC", "PS5")));
    }

    @Test
    void onlineOnlyGameHasAggregatesWithoutRuns() throws Exception {
        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId),
                "{\"totalHours\": 120.0, \"generalRating\": 6, \"platformIds\": [" + switchId + "]}")
                .andExpect(status().isOk());

        performAs(auth, get("/api/v1/games"))
                .andExpect(jsonPath("$[0].experienceCount").value(0))
                .andExpect(jsonPath("$[0].totalHours").value(120.0))
                .andExpect(jsonPath("$[0].bestRating").value(6))
                .andExpect(jsonPath("$[0].platformRefs[*].name", contains("Switch")));
    }

    @Test
    void statsIncludeOnlineRatingsAndHours() throws Exception {
        createExperienceVia(auth, gameId, run("Campaign", "COMPLETADO", 9, 10.0, null, null, null, ""));
        performAs(auth, put("/api/v1/games/{gameId}/online-playtime", gameId),
                "{\"totalHours\": 40.0, \"generalRating\": 7}")
                .andExpect(status().isOk());

        performAs(auth, get("/api/v1/stats/summary"))
                .andExpect(jsonPath("$.averageRating").value(8))
                // The dashboard keeps singleplayer and online hours apart.
                .andExpect(jsonPath("$.totalSingleplayerHours").value(10.0))
                .andExpect(jsonPath("$.totalOnlineHours").value(40.0));

        performAs(auth, get("/api/v1/stats/top-rated"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].runLabel").value("Campaign"))
                .andExpect(jsonPath("$[0].online").value(false))
                .andExpect(jsonPath("$[0].experienceId").isNumber())
                .andExpect(jsonPath("$[1].online").value(true))
                .andExpect(jsonPath("$[1].gameId").value(gameId))
                .andExpect(jsonPath("$[1].experienceId").value(nullValue()))
                .andExpect(jsonPath("$[1].rating").value(7));

        performAs(auth, get("/api/v1/stats/by-saga"))
                .andExpect(jsonPath("$[0].totalHours").value(50.0));
    }
}
