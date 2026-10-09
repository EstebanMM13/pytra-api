package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Genre bucket of the year's "completed in year" runs. */
@Getter
@AllArgsConstructor
public class YearGenreStatDto {
    private Long genreId;
    private String genreName;
    private long experienceCount;
    /** Distinct games. */
    private long gameCount;
    private double totalHours;
}
