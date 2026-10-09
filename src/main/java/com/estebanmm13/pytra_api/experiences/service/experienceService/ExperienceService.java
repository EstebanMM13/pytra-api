package com.estebanmm13.pytra_api.experiences.service.experienceService;

import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceResponseDto;

import java.util.List;

public interface ExperienceService {
    List<ExperienceResponseDto> findAllByGame(Long gameId, Long userId);
    ExperienceResponseDto findById(Long id, Long userId);
    ExperienceResponseDto create(Long gameId, ExperienceRequestDto experienceRequestDto, Long userId);
    ExperienceResponseDto update(Long id, ExperienceRequestDto experienceRequestDto, Long userId);
    void delete(Long id, Long userId);
}
