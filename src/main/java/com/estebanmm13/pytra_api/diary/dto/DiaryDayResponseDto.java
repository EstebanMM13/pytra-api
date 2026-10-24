package com.estebanmm13.pytra_api.diary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A diary day: its note, the hours played that day in total (counted or not) and its games. */
public record DiaryDayResponseDto(
        LocalDate date,
        String note,
        BigDecimal totalHours,
        List<DiaryGameResponseDto> games
) {
}
