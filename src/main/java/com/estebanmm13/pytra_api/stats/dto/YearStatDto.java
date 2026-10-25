package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * One year bucket; see ExperiencePeriod for how an experience is mapped to a year. {@link #totalHours}
 * also includes {@link #onlineHours}; the counts and the rating only use runs.
 */
@Getter
@AllArgsConstructor
public class YearStatDto {
    private Integer year;
    private double totalHours;
    private long experienceCount;
    /** Mean rating of that year's rated experiences, 2 decimals; null when none is rated. */
    private BigDecimal averageRating;
    /** Diary hours of the year counted into online playtime (online playtime itself has no date). */
    private double onlineHours;
}
