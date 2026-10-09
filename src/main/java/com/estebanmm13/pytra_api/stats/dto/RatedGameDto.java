package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** A game ranked by its average rating over the year's "completed in year" runs. */
@Getter
@AllArgsConstructor
public class RatedGameDto {
    private Long gameId;
    private String gameName;
    private Double avgRating;
    private long experienceCount;
    private double totalHours;
}
