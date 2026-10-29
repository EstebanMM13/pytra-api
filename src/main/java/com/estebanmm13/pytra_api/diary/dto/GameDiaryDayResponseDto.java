package com.estebanmm13.pytra_api.diary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A day one game was played (the game detail's "days played"). {@code autoSynced}: the line was written by
 * the Steam/PSN sync (record only).
 */
public record GameDiaryDayResponseDto(
        LocalDate date,
        BigDecimal hours,
        String note,
        Long experienceId,
        boolean countsHours,
        boolean autoSynced
) {
}
