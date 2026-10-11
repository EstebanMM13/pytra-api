package com.estebanmm13.pytra_api.metadata;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IGDB endpoints behind the real security chain. The test profile has no Twitch credentials, so
 * this is the "disabled" deployment: nothing ever reaches Twitch or IGDB.
 */
class IgdbMetadataIntegrationTests extends AbstractIntegrationTest {

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/providers"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/metadata/igdb/search").param("q", "zelda"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/metadata/igdb/1025"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutCredentialsIgdbIsDisabled() throws Exception {
        String auth = bearer(createVerifiedUser("igdb"));

        performAs(auth, get("/api/v1/metadata/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.igdb").value(false))
                .andExpect(jsonPath("$.steam").value(true));
        performAs(auth, get("/api/v1/metadata/igdb/search").param("q", "zelda"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("IGDB_DISABLED"));
        performAs(auth, get("/api/v1/metadata/igdb/1025"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("IGDB_DISABLED"));
    }
}
