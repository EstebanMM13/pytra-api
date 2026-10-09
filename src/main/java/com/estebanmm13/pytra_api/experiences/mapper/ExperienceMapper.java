package com.estebanmm13.pytra_api.experiences.mapper;

import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceResponseDto;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.Ratings;
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
                experience.getPlatform(),
                experience.getPlatinum(),
                experience.getReplay(),
                experience.getSummary(),
                experience.getPros(),
                experience.getCons(),
                experience.getNotes(),
                experience.getUpdatedAt()
        );
    }
}
