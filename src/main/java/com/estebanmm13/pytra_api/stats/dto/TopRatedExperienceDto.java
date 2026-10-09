package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TopRatedExperienceDto {
    private Long gameId;
    private String gameName;
    private String runLabel;
    private BigDecimal rating;
}
