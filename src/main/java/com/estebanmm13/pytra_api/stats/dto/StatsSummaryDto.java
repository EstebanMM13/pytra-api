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
    /** Mean rating of the rated experiences, 2 decimals; null when nothing is rated. */
    private Double averageRating;
    private long replayCount;
    private long completedCount;
    private long abandonedCount;
    private long inProgressCount;
}
