package com.estebanmm13.pytra_api.experiences.dto.experience;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.Platform;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ExperienceResponseDto {
    private Long id;
    private Long gameId;
    private String runLabel;
    private Integer year;
    private ExperienceStatus status;
    private Integer rating;
    private Double hours;
    private LocalDate startDate;
    private LocalDate endDate;
    private Platform platform;
    private Boolean platinum;
    private Boolean replay;
    private String summary;
    private String pros;
    private String cons;
    private String notes;
    private LocalDateTime updatedAt;
}
