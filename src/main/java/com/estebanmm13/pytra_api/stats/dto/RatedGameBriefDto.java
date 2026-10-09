package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/** Surprise / disappointment entry. */
@Getter
@AllArgsConstructor
public class RatedGameBriefDto {
    private Long gameId;
    private String gameName;
    private BigDecimal avgRating;
}
