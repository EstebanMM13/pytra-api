package com.estebanmm13.pytra_api.metadata;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.metadata.client.SteamStoreClient;
import com.estebanmm13.pytra_api.metadata.dto.SteamGameMetadataDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Steam store proxy behind the real security chain; Steam itself is mocked. */
class GameMetadataIntegrationTests extends AbstractIntegrationTest {

    @MockitoBean
    private SteamStoreClient steamStoreClient;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/steam/search").param("q", "portal"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/metadata/steam/620"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserGetsNormalizedDetails() throws Exception {
        when(steamStoreClient.getDetails(620))
                .thenReturn(new SteamGameMetadataDto(620, "Portal 2", "Valve", "Valve", null, List.of("Acción"), null));

        performAs(bearer(createVerifiedUser("meta")), get("/api/v1/metadata/steam/620"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Portal 2"))
                .andExpect(jsonPath("$.genres[0]").value("Acción"));
    }
}
