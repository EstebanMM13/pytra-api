package com.estebanmm13.pytra_api.platforms;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catalog cap set below the seeded size, so the catalog is already full. Own Spring context (and database),
 * since the cap is global and would otherwise depend on what other test classes created.
 */
@TestPropertySource(properties = "app.platforms.max-total=10")
class PlatformCatalogCapIntegrationTests extends AbstractIntegrationTest {

    private String auth;

    @BeforeEach
    void setUp() {
        auth = bearer(createVerifiedUser("platformcap"));
    }

    @Test
    void newPlatformIsRejectedWhenTheCatalogIsFull() throws Exception {
        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"Over The Cap\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("PLATFORM_CATALOG_FULL"));

        performAs(auth, get("/api/v1/platforms"))
                .andExpect(jsonPath("$[*].name", not(hasItem("Over The Cap"))));
    }

    @Test
    void existingPlatformIsStillReturnedWhenTheCatalogIsFull() throws Exception {
        performAs(auth, post("/api/v1/platforms"), "{\"name\": \"gamecube\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("GameCube"));
    }
}
