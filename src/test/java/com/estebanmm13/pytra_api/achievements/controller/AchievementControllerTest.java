package com.estebanmm13.pytra_api.achievements.controller;

import com.estebanmm13.pytra_api.achievements.dto.UnlockedAchievementDto;
import com.estebanmm13.pytra_api.achievements.service.AchievementService;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Docker-free controller checks (standalone MockMvc, mocked service) for the achievements endpoints. */
class AchievementControllerTest {

    private static final Long USER_ID = 5L;
    private static final LocalDateTime FIRST = LocalDateTime.of(2026, 11, 1, 10, 0);

    private final AchievementService achievementService = mock(AchievementService.class);
    private final CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AchievementController(achievementService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(currentUserResolver.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void listsTheCurrentUsersUnlockedAchievements() throws Exception {
        when(achievementService.findUnlocked(USER_ID)).thenReturn(List.of(new UnlockedAchievementDto("years10", FIRST)));

        mockMvc.perform(get("/api/v1/achievements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("years10"))
                .andExpect(jsonPath("$[0].unlockedAt").exists());
    }

    @Test
    void recordsTheIdsForTheCurrentUser() throws Exception {
        when(achievementService.record(USER_ID, List.of("multitasker", "years10")))
                .thenReturn(List.of(new UnlockedAchievementDto("multitasker", FIRST)));

        mockMvc.perform(post("/api/v1/achievements").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [\"multitasker\", \"years10\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("multitasker"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"ids\": [\"\"]}",
            "{\"ids\": [\"   \"]}",
            "{\"ids\": [\"bad id\"]}",
            "{\"ids\": [\"<script>\"]}",
            "{\"ids\": [\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"]}",
            "{\"ids\": [null]}",
            "{\"ids\": []}",
            "{\"ids\": null}",
            "{}"
    })
    void rejectsIdsWithAnUnsafeShape(String body) throws Exception {
        mockMvc.perform(post("/api/v1/achievements").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(achievementService, never()).record(anyLong(), any());
    }

    @Test
    void rejectsTooManyIdsInOneRequest() throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i <= 100; i++) {
            ids.append(i == 0 ? "" : ",").append("\"badge").append(i).append('"');
        }
        mockMvc.perform(post("/api/v1/achievements").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [" + ids + "]}"))
                .andExpect(status().isBadRequest());

        verify(achievementService, never()).record(anyLong(), any());
    }
}
