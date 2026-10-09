package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StatsSummaryDto {
    private long totalGames;
    private long totalSagas;
    private long totalExperiences;
    private double totalSingleplayerHours;
    private double totalOnlineHours;
    private long totalPlatinums;
}
