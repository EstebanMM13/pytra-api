package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class YearStatDto {
    private Integer year;
    private double totalHours;
    private long experienceCount;
}
