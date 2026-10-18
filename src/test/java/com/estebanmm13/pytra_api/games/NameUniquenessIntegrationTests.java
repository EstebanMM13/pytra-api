package com.estebanmm13.pytra_api.games;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Name duplicate checks must fold case exactly like the unique LOWER(name) indexes. The KELVIN SIGN (U+212A)
 * lowercases to an ASCII "k" but has no uppercase of its own, so with an UPPER()-based check "Kelvin" looks
 * new while the index sees it as "kelvin": the pre-check passed and the insert failed on the index instead of
 * answering the domain 409.
 */
class NameUniquenessIntegrationTests extends AbstractIntegrationTest {

    private static final String KELVIN_SIGN = "K";

    private String auth;
    private String suffix;

    @BeforeEach
    void setUp() {
        auth = bearer(createVerifiedUser("names"));
        suffix = UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void creatingAGameWithACaseVariantNameIsADuplicate() throws Exception {
        createGameVia(auth, "Kelvin " + suffix, "");

        performAs(auth, post("/api/v1/games"),
                "{\"name\": \"" + KELVIN_SIGN + "elvin " + suffix + "\", \"category\": \"SINGLEPLAYER\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Game already exists"));
    }

    @Test
    void renamingAGameToACaseVariantOfAnotherIsADuplicate() throws Exception {
        createGameVia(auth, "Kelvin " + suffix, "");
        Long other = createGameVia(auth, "Other " + suffix, "");

        performAs(auth, put("/api/v1/games/{id}", other),
                "{\"name\": \"" + KELVIN_SIGN + "ELVIN " + suffix + "\", \"category\": \"SINGLEPLAYER\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Game already exists"));
    }

    @Test
    void creatingASagaWithACaseVariantNameIsADuplicate() throws Exception {
        performAs(auth, post("/api/v1/sagas"), "{\"name\": \"Kelvin " + suffix + "\"}")
                .andExpect(status().isCreated());

        performAs(auth, post("/api/v1/sagas"), "{\"name\": \"" + KELVIN_SIGN + "elvin " + suffix + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Saga already exists"));
    }

    @Test
    void createIfMissingReturnsThePlatformWithACaseVariantName() throws Exception {
        Long id = readId(performAs(auth, post("/api/v1/platforms"), "{\"name\": \"Kelvin " + suffix + "\"}")
                .andExpect(status().isCreated())
                .andReturn());

        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"" + KELVIN_SIGN + "elvin " + suffix + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }
}
