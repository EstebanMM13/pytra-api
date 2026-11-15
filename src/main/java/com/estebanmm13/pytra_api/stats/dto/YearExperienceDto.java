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
    /** Platinum earned in this year (the postgame end's year, else the credits'; see ExperiencePeriod). */
    private Boolean platinum;
    /** 100% earned in this year, same rule as {@link #platinum}. */
    private Boolean fullCompletion;
    /** Legacy enum code for old clients ("OTHER" for platforms the enum did not have). */
    private String platform;
    private Long platformId;
    private String platformName;
    private String platformColor;
    /** Month (1-12) of the run in the year (credits month, else its last month played), or null. */
    private Integer month;
    private LocalDate startDate;
    private LocalDate endDate;
    /** What the run was in this year: credits, abandonment, postgame or still playing. */
    private YearRunRole yearRole;
    /** The run's hours played in this year (2 decimals); {@link #hours} is the run's total. */
    private Double yearHours;
}
