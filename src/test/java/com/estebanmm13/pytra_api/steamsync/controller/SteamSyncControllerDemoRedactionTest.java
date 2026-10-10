package com.estebanmm13.pytra_api.steamsync.controller;

import com.estebanmm13.pytra_api.auth.security.AuthenticatedUser;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.steamsync.dto.SteamStatusDto;
import com.estebanmm13.pytra_api.steamsync.service.SteamLinkService;
import com.estebanmm13.pytra_api.steamsync.service.SteamSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The demo account is public: the Steam status must not reveal which Steam account is linked to it. */
class SteamSyncControllerDemoRedactionTest {

    private final SteamLinkService steamLinkService = mock(SteamLinkService.class);
    private final CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new SteamSyncController(mock(SteamSyncService.class), steamLinkService, currentUserResolver)).build();
        when(steamLinkService.getStatus(5L)).thenReturn(
                new SteamStatusDto(true, "76561198000000000", "RealPersona", null, true, 42, 3, 1));
    }

    private void signedInAs(boolean demo) {
        when(currentUserResolver.getCurrentUser()).thenReturn(new AuthenticatedUser("owner", 5L, null, demo));
        when(currentUserResolver.getCurrentUserId()).thenReturn(5L);
    }

    @Test
    void demoSessionGetsNoSteamIdentity() throws Exception {
        signedInAs(true);

        mockMvc.perform(get("/api/v1/integrations/steam/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steamId").value(nullValue()))
                .andExpect(jsonPath("$.personaName").value(nullValue()))
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.linkedGamesCount").value(42))
                .andExpect(jsonPath("$.pendingCount").value(3));
    }

    @Test
    void normalSessionGetsTheSteamIdentity() throws Exception {
        signedInAs(false);

        mockMvc.perform(get("/api/v1/integrations/steam/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steamId").value("76561198000000000"))
                .andExpect(jsonPath("$.personaName").value("RealPersona"));
    }
}
