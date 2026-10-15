package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * One rated entry of the all-time ranking: a run ({@code online} false, with experienceId and runLabel) or a
 * game's online playtime ({@code online} true, experienceId and runLabel null, rating = its general rating).
 */
@Getter
@AllArgsConstructor
public class TopRatedExperienceDto {
    private Long gameId;
    private String gameName;
    private String runLabel;
    private BigDecimal rating;
    private Long experienceId;
    private boolean online;
}
