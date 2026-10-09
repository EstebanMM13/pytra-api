package com.estebanmm13.pytra_api.stats.dto;

import com.estebanmm13.pytra_api.experiences.model.Platform;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class InProgressExperienceDto {
    private Long experienceId;
    private Long gameId;
    private String gameName;
    private String coverImageUrl;
    private String runLabel;
    private Platform platform;
    private LocalDate startDate;
    private Double hours;
    private LocalDateTime updatedAt;
}
