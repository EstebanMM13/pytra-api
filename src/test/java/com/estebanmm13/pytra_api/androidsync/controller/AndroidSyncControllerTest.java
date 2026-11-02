package com.estebanmm13.pytra_api.androidsync.controller;

import com.estebanmm13.pytra_api.androidsync.dto.AndroidLinkRequestDto;
import com.estebanmm13.pytra_api.androidsync.dto.AndroidLinkResponseDto;
import com.estebanmm13.pytra_api.androidsync.dto.AndroidUsageDayDto;
import com.estebanmm13.pytra_api.androidsync.dto.AndroidUsageResetResponseDto;
import com.estebanmm13.pytra_api.androidsync.dto.AndroidUsageSyncResponseDto;
import com.estebanmm13.pytra_api.androidsync.service.AndroidLinkService;
import com.estebanmm13.pytra_api.androidsync.service.AndroidUsageResetService;
import com.estebanmm13.pytra_api.androidsync.service.AndroidUsageSyncService;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.error.DuplicateResourceException;
import com.estebanmm13.pytra_api.error.GlobalExceptionHandler;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Docker-free controller checks (standalone MockMvc, mocked services) for the Android sync endpoints. */
class AndroidSyncControllerTest {

    private static final Long USER_ID = 5L;
    private static final String PACKAGE = "com.supercell.clashroyale";

    private final AndroidLinkService linkService = mock(AndroidLinkService.class);
    private final AndroidUsageSyncService syncService = mock(AndroidUsageSyncService.class);
    private final AndroidUsageResetService resetService = mock(AndroidUsageResetService.class);
    private final CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AndroidLinkController(linkService, currentUserResolver),
                        new AndroidUsageController(syncService, resetService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        when(currentUserResolver.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void syncPassesTheDaysOfTheCurrentUserAndAnswersTheSummary() throws Exception {
        List<AndroidUsageDayDto> days = List.of(new AndroidUsageDayDto("2026-11-01", PACKAGE, 75));
        when(syncService.sync(USER_ID, days)).thenReturn(new AndroidUsageSyncResponseDto(1, 0));

        mockMvc.perform(post("/api/v1/android-usage/sync").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"date\":\"2026-11-01\",\"packageName\":\"" + PACKAGE + "\",\"minutes\":75}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value(1))
                .andExpect(jsonPath("$.ignored").value(0));
    }

    @Test
    void resetUndoesTheCurrentUsersAndroidPlay() throws Exception {
        when(resetService.reset(USER_ID)).thenReturn(new AndroidUsageResetResponseDto(2, 1500));

        mockMvc.perform(post("/api/v1/android-usage/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links").value(2))
                .andExpect(jsonPath("$.minutesRemoved").value(1500));
    }

    @Test
    void linksListTheCurrentUsersLinks() throws Exception {
        when(linkService.findAll(USER_ID)).thenReturn(List.of(new AndroidLinkResponseDto(PACKAGE, 42L)));

        mockMvc.perform(get("/api/v1/android-links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].packageName").value(PACKAGE))
                .andExpect(jsonPath("$[0].gameId").value(42));
    }

    @Test
    void createLinkAnswers201() throws Exception {
        when(linkService.create(USER_ID, new AndroidLinkRequestDto(PACKAGE, 42L)))
                .thenReturn(new AndroidLinkResponseDto(PACKAGE, 42L));

        mockMvc.perform(post("/api/v1/android-links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packageName\":\"" + PACKAGE + "\",\"gameId\":42}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.packageName").value(PACKAGE));
    }

    @Test
    void createLinkRejectsAnInvalidPackageName() throws Exception {
        mockMvc.perform(post("/api/v1/android-links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packageName\":\"not a package\",\"gameId\":42}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/android-links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packageName\":\"" + PACKAGE + "\"}"))
                .andExpect(status().isBadRequest());
        verify(linkService, never()).create(any(), any());
    }

    @Test
    void createLinkOfAForeignGameIs404AndADuplicateIs409() throws Exception {
        when(linkService.create(eq(USER_ID), any())).thenThrow(new ResourceNotFoundException("Game not found"));
        mockMvc.perform(post("/api/v1/android-links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packageName\":\"" + PACKAGE + "\",\"gameId\":99}"))
                .andExpect(status().isNotFound());

        when(linkService.create(eq(USER_ID), any())).thenThrow(new DuplicateResourceException("Android app already linked"));
        mockMvc.perform(post("/api/v1/android-links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packageName\":\"" + PACKAGE + "\",\"gameId\":42}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteLinkKeepsTheDotsOfThePackageName() throws Exception {
        mockMvc.perform(delete("/api/v1/android-links/{packageName}", PACKAGE))
                .andExpect(status().isNoContent());
        verify(linkService).delete(USER_ID, PACKAGE);

        doThrow(new ResourceNotFoundException("Android link not found")).when(linkService).delete(USER_ID, "com.other.app");
        mockMvc.perform(delete("/api/v1/android-links/{packageName}", "com.other.app"))
                .andExpect(status().isNotFound());
    }
}
