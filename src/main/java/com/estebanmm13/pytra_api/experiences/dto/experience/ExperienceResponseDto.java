package com.estebanmm13.pytra_api.experiences.dto.experience;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
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
    private BigDecimal rating;
    private Double hours;
    private LocalDate startDate;
    private LocalDate endDate;
    /** Legacy enum code for old clients ("OTHER" for platforms the enum did not have). */
    private String platform;
    private Long platformId;
    private String platformName;
    private String platformColor;
    private Boolean platinum;
    private Boolean replay;
    private String summary;
    private String pros;
    private String cons;
    private String notes;
    private LocalDateTime updatedAt;
}
