package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SagaStatDto {
    private Long sagaId;
    private String sagaName;
    private long gameCount;
    private double totalHours;
}
