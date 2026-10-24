package com.estebanmm13.pytra_api.experiences.service.experienceService;

import com.estebanmm13.pytra_api.experiences.dto.experience.ExperiencePatchDto;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceResponseDto;

import java.math.BigDecimal;
import java.util.List;

public interface ExperienceService {
    List<ExperienceResponseDto> findAllByGame(Long gameId, Long userId);
    ExperienceResponseDto findById(Long id, Long userId);
    ExperienceResponseDto create(Long gameId, ExperienceRequestDto experienceRequestDto, Long userId);
    ExperienceResponseDto update(Long id, ExperienceRequestDto experienceRequestDto, Long userId);
    ExperienceResponseDto patch(Long id, ExperiencePatchDto experiencePatchDto, Long userId);
    void delete(Long id, Long userId);
    /**
     * Adds {@code deltaHours} (negative subtracts) to the user's run, never going below 0, through the same
     * reconciled save as the run updates. Used by the play diary.
     */
    void addHours(Long id, BigDecimal deltaHours, Long userId);
}
