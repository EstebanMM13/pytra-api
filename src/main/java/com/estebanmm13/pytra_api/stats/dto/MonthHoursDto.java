package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MonthHoursDto {
    /** 1 (January) to 12 (December). */
    private int month;
    private double hours;
}
