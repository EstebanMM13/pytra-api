package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class StatsSummaryDto {
    private long totalGames;
    private long totalSagas;
    private long totalExperiences;
    private double totalSingleplayerHours;
    private double totalOnlineHours;
    /** Platinum runs plus platinum online playtimes (online has no year, so year summaries count runs only). */
    private long totalPlatinums;
    /** 100% runs plus 100% online playtimes, like {@link #totalPlatinums}. */
    private long totalFullCompletions;
    /** Mean of the run ratings and online general ratings, 2 decimals; null when nothing is rated. */
    private BigDecimal averageRating;
    private long replayCount;
    private long completedCount;
    private long abandonedCount;
    private long inProgressCount;
}
