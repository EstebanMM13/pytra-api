package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Saga bucket of the year's "completed in year" runs. */
@Getter
@AllArgsConstructor
public class YearSagaStatDto {
    private Long sagaId;
    private String sagaName;
    private long experienceCount;
    /** Distinct games. */
    private long gameCount;
    private double totalHours;
}
