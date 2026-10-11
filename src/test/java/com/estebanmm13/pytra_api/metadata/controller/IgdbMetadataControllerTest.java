package com.estebanmm13.pytra_api.metadata.controller;

import com.estebanmm13.pytra_api.error.GlobalExceptionHandler;
import com.estebanmm13.pytra_api.error.IgdbIntegrationException;
import com.estebanmm13.pytra_api.metadata.client.IgdbClient;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.IgdbSearchResultDto;
import com.estebanmm13.pytra_api.metadata.service.IgdbMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Docker-free controller checks (standalone MockMvc: routing, validation and the error format).
 * Authentication is enforced by SecurityConfig, see IgdbMetadataIntegrationTests.
 */
class IgdbMetadataControllerTest {

    private final IgdbClient client = mock(IgdbClient.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(client.isEnabled()).thenReturn(true);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new IgdbMetadataController(new IgdbMetadataService(client)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void providersReportIgdbAndSteam() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.igdb").value(true))
                .andExpect(jsonPath("$.steam").value(true));
    }

    @Test
    void searchReturnsHits() throws Exception {
        when(client.search("knack", 10)).thenReturn(List.of(
                new IgdbSearchResultDto(7, "Knack", 2013, "https://images.igdb.com/x.jpg", List.of("PS4"))));

        mockMvc.perform(get("/api/v1/metadata/igdb/search").param("q", "knack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].igdbId").value(7))
                .andExpect(jsonPath("$[0].year").value(2013))
                .andExpect(jsonPath("$[0].imageUrl").value("https://images.igdb.com/x.jpg"))
                .andExpect(jsonPath("$[0].platforms[0]").value("PS4"));
    }

    @Test
    void detailsReturnTheSteamLikeShape() throws Exception {
        when(client.getDetails(7)).thenReturn(new IgdbGameMetadataDto(7, "Knack", "SIE Japan Studio",
                "Sony Interactive Entertainment", LocalDate.of(2013, 11, 15), List.of("Platform"),
                "https://images.igdb.com/c.jpg", List.of("PlayStation 4")));

        mockMvc.perform(get("/api/v1/metadata/igdb/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.igdbId").value(7))
                .andExpect(jsonPath("$.developer").value("SIE Japan Studio"))
                .andExpect(jsonPath("$.releaseDate").value("2013-11-15"))
                .andExpect(jsonPath("$.genres[0]").value("Platform"))
                .andExpect(jsonPath("$.coverImageUrl").value("https://images.igdb.com/c.jpg"))
                .andExpect(jsonPath("$.platforms[0]").value("PlayStation 4"));
    }

    @Test
    void validationErrorsUseStableCodes() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/igdb/search").param("q", "k"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_SEARCH_QUERY"));
        mockMvc.perform(get("/api/v1/metadata/igdb/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_IGDB_ID"));
    }

    @Test
    void igdbFailuresUseStableCodes() throws Exception {
        when(client.getDetails(1)).thenThrow(IgdbIntegrationException.unavailable());
        when(client.getDetails(2)).thenThrow(IgdbIntegrationException.gameNotFound());

        mockMvc.perform(get("/api/v1/metadata/igdb/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("IGDB_UNAVAILABLE"));
        mockMvc.perform(get("/api/v1/metadata/igdb/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("IGDB_GAME_NOT_FOUND"));
    }

    @Test
    void disabledIgdbIsNotFound() throws Exception {
        when(client.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/v1/metadata/igdb/search").param("q", "knack"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("IGDB_DISABLED"));
        mockMvc.perform(get("/api/v1/metadata/providers"))
                .andExpect(jsonPath("$.igdb").value(false));
    }
}
