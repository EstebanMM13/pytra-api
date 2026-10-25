package com.estebanmm13.pytra_api.experiences.mapper;

import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceResponseDto;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.Ratings;
import com.estebanmm13.pytra_api.platforms.mapper.PlatformMapper;
import org.springframework.stereotype.Component;

@Component
public class ExperienceMapper {

    public ExperienceResponseDto toResponseDto(Experience experience) {
        if (experience == null) return null;
        return new ExperienceResponseDto(
                experience.getId(),
                experience.getGameId(),
                experience.getRunLabel(),
                experience.getYear(),
                experience.getStatus(),
                Ratings.normalize(experience.getRating()),
                experience.getHours(),
                experience.getStartDate(),
                experience.getEndDate(),
                PlatformMapper.legacyCodeOf(experience.getPlatform()),
                PlatformMapper.idOf(experience.getPlatform()),
                PlatformMapper.nameOf(experience.getPlatform()),
                PlatformMapper.colorOf(experience.getPlatform()),
                experience.getPlatinum(),
                experience.getFullCompletion(),
                experience.getReplay(),
                experience.getSummary(),
                experience.getPros(),
                experience.getCons(),
                experience.getNotes(),
                experience.getUpdatedAt()
        );
    }
}
