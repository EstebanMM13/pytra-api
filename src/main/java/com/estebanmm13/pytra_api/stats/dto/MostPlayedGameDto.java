package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MostPlayedGameDto {
    private Long gameId;
    private String gameName;
    private double totalHours;
}
