package com.estebanmm13.pytra_api.psnsync.controller;

import com.estebanmm13.pytra_api.auth.security.AuthenticatedUser;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import com.estebanmm13.pytra_api.psnsync.dto.PsnStatusDto;
import com.estebanmm13.pytra_api.psnsync.service.PsnLinkService;
import com.estebanmm13.pytra_api.psnsync.service.PsnSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The demo account is public: the PSN status must not reveal which PSN account is linked to it.
 * The service account's health (expired NPSSO) is for admins only.
 */
class PsnSyncControllerRedactionTest {

    private static final Instant FAILING_SINCE = Instant.parse("2026-10-01T10:00:00Z");

    private final PsnLinkService psnLinkService = mock(PsnLinkService.class);
    private final CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PsnSyncController(mock(PsnSyncService.class), psnLinkService, currentUserResolver)).build();
        PsnStatusDto status = new PsnStatusDto(true, "962157895908076652", "EstebanK13", null, true, 42, 3, 1,
                PsnIntegrationException.SERVICE_AUTH_FAILED, FAILING_SINCE);
        when(psnLinkService.getStatus(5L)).thenReturn(status);
        when(psnLinkService.link(5L, "EstebanK13")).thenReturn(status);
    }

    private void signedInAs(boolean demo, boolean admin) {
        when(currentUserResolver.getCurrentUser()).thenReturn(new AuthenticatedUser("owner", 5L, null, demo));
        when(currentUserResolver.getCurrentUserId()).thenReturn(5L);
        when(currentUserResolver.isAdmin()).thenReturn(admin);
    }

    @Test
    void demoSessionGetsNoPsnIdentity() throws Exception {
        signedInAs(true, false);

        mockMvc.perform(get("/api/v1/integrations/psn/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(nullValue()))
                .andExpect(jsonPath("$.onlineId").value(nullValue()))
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.linkedGamesCount").value(42))
                .andExpect(jsonPath("$.pendingCount").value(3));
    }

    @Test
    void regularUserSeesTheIdentityButNotTheServiceAccountHealth() throws Exception {
        signedInAs(false, false);

        mockMvc.perform(get("/api/v1/integrations/psn/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineId").value("EstebanK13"))
                .andExpect(jsonPath("$.accountId").value("962157895908076652"))
                .andExpect(jsonPath("$.serviceAuthError").value(nullValue()))
                .andExpect(jsonPath("$.serviceAuthFailingSince").value(nullValue()));
    }

    @Test
    void adminSeesTheServiceAccountFailure() throws Exception {
        signedInAs(false, true);

        mockMvc.perform(get("/api/v1/integrations/psn/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceAuthError").value("PSN_SERVICE_AUTH_FAILED"))
                .andExpect(jsonPath("$.serviceAuthFailingSince").isNotEmpty());
    }

    @Test
    void linkAnswersTheRedactedStatus() throws Exception {
        signedInAs(false, false);

        mockMvc.perform(put("/api/v1/integrations/psn/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineId\":\"EstebanK13\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineId").value("EstebanK13"))
                .andExpect(jsonPath("$.serviceAuthError").value(nullValue()));
    }
}
