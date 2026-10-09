package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** One year bucket; see ExperiencePeriod for how an experience is mapped to a year. */
@Getter
@AllArgsConstructor
public class YearStatDto {
    private Integer year;
    private double totalHours;
    private long experienceCount;
    /** Mean rating of that year's rated experiences, 2 decimals; null when none is rated. */
    private Double averageRating;
}
