package com.estebanmm13.pytra_api.experiences.controller;

import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.error.GlobalExceptionHandler;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperiencePatchDto;
import com.estebanmm13.pytra_api.experiences.service.experienceService.ExperienceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Docker-free controller checks (standalone MockMvc, mocked service) for the run endpoints' validation. */
class ExperienceControllerTest {

    private static final Long USER_ID = 5L;

    private final ExperienceService experienceService = mock(ExperienceService.class);
    private final CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExperienceController(experienceService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(currentUserResolver.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void patchPassesTheSentFiguresToTheService() throws Exception {
        mockMvc.perform(patch("/api/v1/experiences/11").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\": 12.5, \"rating\": 8.75}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ExperiencePatchDto> captor = ArgumentCaptor.forClass(ExperiencePatchDto.class);
        verify(experienceService).patch(eq(11L), captor.capture(), eq(USER_ID));
        assertThat(captor.getValue().getHours()).isEqualTo(12.5);
        assertThat(captor.getValue().getRating()).isEqualByComparingTo(new BigDecimal("8.75"));
        assertThat(captor.getValue().isRatingSent()).isTrue();
    }

    @Test
    void patchTellsAnOmittedRatingFromAClearedOne() throws Exception {
        mockMvc.perform(patch("/api/v1/experiences/11").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\": 3}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/experiences/11").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ExperiencePatchDto> captor = ArgumentCaptor.forClass(ExperiencePatchDto.class);
        verify(experienceService, times(2)).patch(eq(11L), captor.capture(), eq(USER_ID));
        assertThat(captor.getAllValues().get(0).isRatingSent()).isFalse();
        assertThat(captor.getAllValues().get(1).isRatingSent()).isTrue();
        assertThat(captor.getAllValues().get(1).getRating()).isNull();
    }

    @Test
    void patchRejectsInvalidFiguresWithFieldErrors() throws Exception {
        mockMvc.perform(patch("/api/v1/experiences/11").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hours").exists());
        mockMvc.perform(patch("/api/v1/experiences/11").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 10.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").exists());
        mockMvc.perform(patch("/api/v1/experiences/11").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 9.255}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").exists());

        verify(experienceService, never()).patch(any(), any(), any());
    }

    @Test
    void patchOfAForeignOrMissingRunIs404() throws Exception {
        when(experienceService.patch(eq(99L), any(), eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("Experience not found"));

        mockMvc.perform(patch("/api/v1/experiences/99").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\": 1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsOutOfRangeDatesWithFieldErrors() throws Exception {
        mockMvc.perform(put("/api/v1/experiences/11").contentType(MediaType.APPLICATION_JSON)
                        .content(run("\"1969-12-31\"", "\"2024-01-01\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.startDate").exists());
        mockMvc.perform(put("/api/v1/experiences/11").contentType(MediaType.APPLICATION_JSON)
                        .content(run("null", "\"9999-01-01\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists());
        mockMvc.perform(put("/api/v1/experiences/11").contentType(MediaType.APPLICATION_JSON)
                        .content(run("\"2024-03-10\"", "\"2024-03-09\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").value("must not be before the start date"));

        verify(experienceService, never()).update(any(), any(), any());
    }

    private static String run(String startDate, String endDate) {
        return "{\"runLabel\": \"Run 1\", \"status\": \"COMPLETADO\", \"platform\": \"PC\", "
                + "\"startDate\": " + startDate + ", \"endDate\": " + endDate + "}";
    }
}
