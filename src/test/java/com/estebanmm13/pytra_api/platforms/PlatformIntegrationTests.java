package com.estebanmm13.pytra_api.platforms;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformIntegrationTests extends AbstractIntegrationTest {

    private String auth;

    @BeforeEach
    void setUp() {
        auth = bearer(createVerifiedUser("platforms"));
    }

    @Test
    void catalogIsSeededInOrderWithLegacyCodes() throws Exception {
        MvcResult result = performAs(auth, get("/api/v1/platforms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("PC"))
                .andExpect(jsonPath("$[0].legacyCode").value("PC"))
                .andExpect(jsonPath("$[0].family").value("PC"))
                .andExpect(jsonPath("$[1].name").value("PS5"))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        List<String> names = JsonPath.read(json, "$[*].name");
        assertThat(names).contains("PS Vita", "Xbox Series", "Xbox One", "Xbox 360", "Switch 2", "GameCube",
                "Game Boy Advance", "DS", "Móvil", "Retro / Emulador");
        // Seeded entries come first, in their sort order.
        assertThat(names.subList(0, 24)).containsSubsequence("PC", "PS5", "PS4", "PS3", "Xbox Series", "Xbox",
                "Switch 2", "Switch", "DS", "Game Boy", "Móvil", "Retro / Emulador");

        assertThat(legacyCodeOf(json, "PS4")).isEqualTo("PS4");
        assertThat(legacyCodeOf(json, "Xbox")).isEqualTo("XBOX");
        assertThat(legacyCodeOf(json, "Switch")).isEqualTo("SWITCH");
        assertThat(legacyCodeOf(json, "Móvil")).isEqualTo("MOBILE");
        assertThat(legacyCodeOf(json, "Xbox One")).isNull();
        List<String> dsColor = JsonPath.read(json, "$[?(@.name == 'DS')].color");
        assertThat(dsColor).containsExactly("#E0524C");
    }

    @Test
    void createIfMissingDedupesCaseInsensitively() throws Exception {
        String name = "Anbernic " + suffix();

        MvcResult created = performAs(auth, post("/api/v1/platforms"), "{\"name\": \"  " + name + "  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.family").value("OTHER"))
                .andExpect(jsonPath("$.color").value("#9B7BD4"))
                .andExpect(jsonPath("$.legacyCode").value(nullValue()))
                .andReturn();
        Long id = readId(created);

        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"" + name.toUpperCase() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(name));

        // Global catalog: another user sees it and gets the same entry back.
        String otherAuth = bearer(createVerifiedUser("platforms2"));
        performAs(otherAuth, get("/api/v1/platforms"))
                .andExpect(jsonPath("$[*].name", hasItem(name)));
        performAs(otherAuth, post("/api/v1/platforms"), "{\"name\": \"" + name.toLowerCase() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void seededNamesAreDedupedToo() throws Exception {
        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"ps vita\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("PS Vita"))
                .andExpect(jsonPath("$.family").value("PLAYSTATION"));
    }

    @Test
    void createUsesFamilyAndColor() throws Exception {
        String name = "Handheld " + suffix();
        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"" + name + "\", \"family\": \"NINTENDO\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.family").value("NINTENDO"))
                .andExpect(jsonPath("$.color").value("#E0524C"));

        String colored = "Colored " + suffix();
        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"" + colored + "\", \"color\": \"#a1b2c3\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.color").value("#A1B2C3"));
    }

    @Test
    void creatingTooManyPlatformsIsRateLimitedPerUser() throws Exception {
        String prefix = "Burst " + suffix() + " ";
        for (int i = 0; i < 20; i++) {
            performAs(auth, post("/api/v1/platforms"), "{\"name\": \"" + prefix + i + "\"}")
                    .andExpect(status().isCreated());
        }

        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"" + prefix + "20\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value("PLATFORM_RATE_LIMITED"));

        // Resolving an existing entry is not a creation, so it still works for the throttled user.
        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"" + (prefix + 0).toLowerCase() + "\"}")
                .andExpect(status().isOk());
        // The limit is per user.
        performAs(bearer(createVerifiedUser("platforms3")), post("/api/v1/platforms"),
                "{\"name\": \"" + prefix + "20\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void invalidCreateRequestsAre400() throws Exception {
        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"   \"}")
                .andExpect(status().isBadRequest());
        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"" + "x".repeat(41) + "\"}")
                .andExpect(status().isBadRequest());
        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"Bad color " + suffix() + "\", \"color\": \"red\"}")
                .andExpect(status().isBadRequest());
        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"Bad family\", \"family\": \"SEGA\"}")
                .andExpect(status().isBadRequest());
        performAs(auth, get("/api/v1/platforms"))
                .andExpect(jsonPath("$[*].name", not(hasItem("Bad family"))));
    }

    @Test
    void experienceWithPlatformIdReturnsCatalogFields() throws Exception {
        Long dsId = platformIdByName("DS");
        Long gameId = createGameVia(auth, "Pokemon Platino", "");

        Long runId = createExperienceVia(auth, gameId,
                "{\"runLabel\": \"Run 1\", \"status\": \"COMPLETADO\", \"platformId\": " + dsId + "}");

        performAs(auth, get("/api/v1/experiences/{id}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformId").value(dsId))
                .andExpect(jsonPath("$.platformName").value("DS"))
                .andExpect(jsonPath("$.platformColor").value("#E0524C"))
                // Old clients only know the six enum values.
                .andExpect(jsonPath("$.platform").value("OTHER"));

        performAs(auth, get("/api/v1/games/{id}", gameId))
                .andExpect(jsonPath("$.platforms[0]").value("OTHER"))
                .andExpect(jsonPath("$.platformRefs[0].id").value(dsId))
                .andExpect(jsonPath("$.platformRefs[0].name").value("DS"));
    }

    @Test
    void legacyPlatformCodeIsStillAccepted() throws Exception {
        Long gameId = createGameVia(auth, "Legacy", "");

        Long runId = createExperienceVia(auth, gameId,
                "{\"runLabel\": \"Old app\", \"status\": \"EN_CURSO\", \"platform\": \"XBOX\"}");

        performAs(auth, get("/api/v1/experiences/{id}", runId))
                .andExpect(jsonPath("$.platform").value("XBOX"))
                .andExpect(jsonPath("$.platformName").value("Xbox"))
                .andExpect(jsonPath("$.platformId").value(platformIdByName("Xbox")));

        // platformId wins over the legacy code.
        Long ps3 = platformIdByName("PS3");
        performAs(auth, put("/api/v1/experiences/{id}", runId),
                "{\"runLabel\": \"Old app\", \"status\": \"EN_CURSO\", \"platform\": \"PC\", \"platformId\": " + ps3 + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformName").value("PS3"))
                .andExpect(jsonPath("$.platform").value("OTHER"));
    }

    @Test
    void oldClientEchoingOtherKeepsTheCurrentPlatform() throws Exception {
        Long gbaId = platformIdByName("Game Boy Advance");
        Long gameId = createGameVia(auth, "Echo", "");
        Long runId = createExperienceVia(auth, gameId,
                "{\"runLabel\": \"New app\", \"status\": \"EN_CURSO\", \"platformId\": " + gbaId + "}");

        // An old client edits the run: it only knows the legacy "OTHER" it was given and sends it back.
        performAs(auth, put("/api/v1/experiences/{id}", runId),
                "{\"runLabel\": \"Edited\", \"status\": \"COMPLETADO\", \"platform\": \"OTHER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runLabel").value("Edited"))
                .andExpect(jsonPath("$.platformId").value(gbaId));

        // Picking a real legacy value still changes it.
        performAs(auth, put("/api/v1/experiences/{id}", runId),
                "{\"runLabel\": \"Edited\", \"status\": \"COMPLETADO\", \"platform\": \"SWITCH\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformName").value("Switch"));
    }

    @Test
    void missingOrUnknownPlatformIs400() throws Exception {
        Long gameId = createGameVia(auth, "Invalid", "");

        performAs(auth, post("/api/v1/games/{id}/experiences", gameId),
                "{\"runLabel\": \"None\", \"status\": \"EN_CURSO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_PLATFORM"));
        performAs(auth, post("/api/v1/games/{id}/experiences", gameId),
                "{\"runLabel\": \"Other\", \"status\": \"EN_CURSO\", \"platform\": \"OTHER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_PLATFORM"));
        performAs(auth, post("/api/v1/games/{id}/experiences", gameId),
                "{\"runLabel\": \"Ghost\", \"status\": \"EN_CURSO\", \"platformId\": 999999999}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_PLATFORM"));
    }

    @Test
    void steamStylePcRunsStayOnTheSeededPcEntry() throws Exception {
        Long gameId = createGameVia(auth, "Stats", "");
        createExperienceVia(auth, gameId, run("Mine", "EN_CURSO", null, 2.0, null, "2025-01-01", null, ""));

        performAs(auth, get("/api/v1/stats/in-progress"))
                .andExpect(jsonPath("$[0].platform").value("PC"))
                .andExpect(jsonPath("$[0].platformId").value(platformIdByName("PC")))
                .andExpect(jsonPath("$[0].platformName").value("PC"));
    }

    private Long platformIdByName(String name) throws Exception {
        String json = performAs(auth, get("/api/v1/platforms")).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(json, "$[?(@.name == '" + name + "')].id");
        assertThat(ids).hasSize(1);
        return ids.getFirst().longValue();
    }

    private static String legacyCodeOf(String json, String name) {
        List<String> codes = JsonPath.read(json, "$[?(@.name == '" + name + "')].legacyCode");
        assertThat(codes).hasSize(1);
        return codes.getFirst();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
