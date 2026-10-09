package com.estebanmm13.pytra_api.stats.dto;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.Platform;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class YearExperienceDto {
    private Long experienceId;
    private Long gameId;
    private String gameName;
    private String coverImageUrl;
    private String runLabel;
    private ExperienceStatus status;
    private Double hours;
    private BigDecimal rating;
    private Boolean platinum;
    private Platform platform;
    /** Month (1-12) the run counts toward, or null when no date falls in the year. */
    private Integer month;
    private LocalDate startDate;
    private LocalDate endDate;
}
