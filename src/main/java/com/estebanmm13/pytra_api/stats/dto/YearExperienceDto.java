package com.estebanmm13.pytra_api.stats.dto;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
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
    private Boolean fullCompletion;
    /** Legacy enum code for old clients ("OTHER" for platforms the enum did not have). */
    private String platform;
    private Long platformId;
    private String platformName;
    private String platformColor;
    /** Month (1-12) the run counts toward, or null when no date falls in the year. */
    private Integer month;
    private LocalDate startDate;
    private LocalDate endDate;
}
