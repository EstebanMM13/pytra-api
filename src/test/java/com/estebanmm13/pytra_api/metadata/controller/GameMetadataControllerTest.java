package com.estebanmm13.pytra_api.metadata.controller;

import com.estebanmm13.pytra_api.error.GlobalExceptionHandler;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.metadata.client.SteamStoreClient;
import com.estebanmm13.pytra_api.metadata.dto.SteamGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.SteamStoreSearchResultDto;
import com.estebanmm13.pytra_api.metadata.service.SteamStoreMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Docker-free controller checks (standalone MockMvc: routing, validation and the error format).
 * Authentication is enforced by SecurityConfig's {@code anyRequest().authenticated()}, not here.
 */
class GameMetadataControllerTest {

    private final SteamStoreClient client = mock(SteamStoreClient.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GameMetadataController(new SteamStoreMetadataService(client)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchReturnsHits() throws Exception {
        when(client.search("portal", 10)).thenReturn(List.of(new SteamStoreSearchResultDto(620, "Portal 2", "https://img")));

        mockMvc.perform(get("/api/v1/metadata/steam/search").param("q", "portal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appId").value(620))
                .andExpect(jsonPath("$[0].name").value("Portal 2"))
                .andExpect(jsonPath("$[0].imageUrl").value("https://img"));
    }

    @Test
    void searchRejectsShortOrMissingQuery() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/steam/search").param("q", "p"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_SEARCH_QUERY"));
        mockMvc.perform(get("/api/v1/metadata/steam/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_SEARCH_QUERY"));

        verify(client, never()).search(anyString(), anyInt());
    }

    @Test
    void detailsReturnNormalizedMetadata() throws Exception {
        when(client.getDetails(1245620)).thenReturn(new SteamGameMetadataDto(1245620, "ELDEN RING", "FromSoftware, Inc.",
                "FromSoftware, Inc.", LocalDate.of(2022, 2, 24), List.of("Acción", "Rol"), "https://cover"));

        mockMvc.perform(get("/api/v1/metadata/steam/1245620"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steamAppId").value(1245620))
                .andExpect(jsonPath("$.releaseDate").value("2022-02-24"))
                .andExpect(jsonPath("$.genres[1]").value("Rol"))
                .andExpect(jsonPath("$.coverImageUrl").value("https://cover"));
    }

    @Test
    void detailsRejectNonNumericAppId() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/steam/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_STEAM_APP_ID"));
    }

    @Test
    void steamFailuresUseStableCodes() throws Exception {
        when(client.getDetails(1)).thenThrow(SteamIntegrationException.unavailable());
        when(client.getDetails(2)).thenThrow(SteamIntegrationException.appNotFound());
        when(client.getDetails(3)).thenThrow(SteamIntegrationException.appNotAGame());

        mockMvc.perform(get("/api/v1/metadata/steam/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("STEAM_UNAVAILABLE"));
        mockMvc.perform(get("/api/v1/metadata/steam/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("STEAM_APP_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/metadata/steam/3"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.message").value("STEAM_APP_NOT_A_GAME"));
    }
}
