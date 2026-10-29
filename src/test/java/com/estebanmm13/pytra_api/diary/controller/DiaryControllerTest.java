package com.estebanmm13.pytra_api.diary.controller;

import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.diary.dto.DiaryDayRequestDto;
import com.estebanmm13.pytra_api.diary.dto.DiaryDayResponseDto;
import com.estebanmm13.pytra_api.diary.dto.GameDiaryDayResponseDto;
import com.estebanmm13.pytra_api.diary.service.DiaryService;
import com.estebanmm13.pytra_api.error.GlobalExceptionHandler;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Docker-free controller checks (standalone MockMvc, mocked service) for the diary endpoints. */
class DiaryControllerTest {

    private static final Long USER_ID = 5L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    private final DiaryService diaryService = mock(DiaryService.class);
    private final CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DiaryController(diaryService, currentUserResolver),
                        new GameDiaryController(diaryService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(currentUserResolver.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void rangePassesTheDatesOfTheCurrentUser() throws Exception {
        when(diaryService.findRange(DAY, DAY.plusDays(6), USER_ID))
                .thenReturn(List.of(new DiaryDayResponseDto(DAY, null, new BigDecimal("2.5"), List.of())));

        mockMvc.perform(get("/api/v1/diary").param("from", "2026-10-20").param("to", "2026-10-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-10-20"))
                .andExpect(jsonPath("$[0].totalHours").value(2.5));
    }

    @Test
    void aMissingDayIsNotFound() throws Exception {
        when(diaryService.findDay(DAY, USER_ID)).thenThrow(new ResourceNotFoundException("Diary entry not found"));

        mockMvc.perform(get("/api/v1/diary/2026-10-20")).andExpect(status().isNotFound());
    }

    @Test
    void putPassesTheWholeDayToTheService() throws Exception {
        when(diaryService.saveDay(eq(DAY), any(), eq(USER_ID)))
                .thenReturn(Optional.of(new DiaryDayResponseDto(DAY, "Good day", new BigDecimal("3"), List.of())));

        mockMvc.perform(put("/api/v1/diary/2026-10-20").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Good day\", \"games\": [{\"gameId\": 3, \"experienceId\": 11, \"hours\": 1.25, \"note\": \"Boss\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("Good day"));

        ArgumentCaptor<DiaryDayRequestDto> captor = ArgumentCaptor.forClass(DiaryDayRequestDto.class);
        verify(diaryService).saveDay(eq(DAY), captor.capture(), eq(USER_ID));
        assertThat(captor.getValue().getGames()).singleElement().satisfies(game -> {
            assertThat(game.getGameId()).isEqualTo(3L);
            assertThat(game.getExperienceId()).isEqualTo(11L);
            assertThat(game.getHours()).isEqualByComparingTo("1.25");
            assertThat(game.getNote()).isEqualTo("Boss");
        });
    }

    @Test
    void anEmptyDayAnswersNoContent() throws Exception {
        when(diaryService.saveDay(eq(DAY), any(), eq(USER_ID))).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/diary/2026-10-20").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"games\": []}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidHoursAreRejectedWithFieldErrors() throws Exception {
        for (String hours : new String[]{"0", "-1", "24.5", "1.255"}) {
            mockMvc.perform(put("/api/v1/diary/2026-10-20").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"games\": [{\"gameId\": 3, \"hours\": " + hours + "}]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors['games[0].hours']").exists());
        }
        mockMvc.perform(put("/api/v1/diary/2026-10-20").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"games\": [{\"hours\": 1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['games[0].gameId']").exists());
        verify(diaryService, never()).saveDay(any(), any(), any());
    }

    @Test
    void anAmbiguousRunNamesTheGameInFieldErrors() throws Exception {
        when(diaryService.saveDay(eq(DAY), any(), eq(USER_ID))).thenThrow(new InvalidRequestException(
                InvalidRequestException.DIARY_RUN_REQUIRED,
                Map.of("games[0].experienceId", InvalidRequestException.DIARY_RUN_REQUIRED)));

        mockMvc.perform(put("/api/v1/diary/2026-10-20").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"games\": [{\"gameId\": 3, \"hours\": 1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("DIARY_RUN_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors['games[0].experienceId']").value("DIARY_RUN_REQUIRED"));
    }

    @Test
    void aMalformedDateIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/diary/20-10-2026")).andExpect(status().isBadRequest());
    }

    @Test
    void deleteAnswersNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/diary/2026-10-20")).andExpect(status().isNoContent());
        verify(diaryService).deleteDay(DAY, USER_ID);
    }

    @Test
    void deletingAMissingDayIsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Diary entry not found")).when(diaryService).deleteDay(DAY, USER_ID);

        mockMvc.perform(delete("/api/v1/diary/2026-10-20")).andExpect(status().isNotFound());
    }

    @Test
    void gameDiaryListsTheDaysOfTheGame() throws Exception {
        when(diaryService.findByGame(3L, USER_ID))
                .thenReturn(List.of(new GameDiaryDayResponseDto(DAY, new BigDecimal("2"), null, 11L, true, false)));

        mockMvc.perform(get("/api/v1/games/3/diary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-10-20"))
                .andExpect(jsonPath("$[0].experienceId").value(11));
    }
}
