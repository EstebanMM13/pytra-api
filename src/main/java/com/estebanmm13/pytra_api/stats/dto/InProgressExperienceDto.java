package com.estebanmm13.pytra_api.stats.dto;

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
    /** Legacy enum code for old clients ("OTHER" for platforms the enum did not have). */
    private String platform;
    private Long platformId;
    private String platformName;
    private String platformColor;
    private LocalDate startDate;
    private Double hours;
    private LocalDateTime updatedAt;
}
