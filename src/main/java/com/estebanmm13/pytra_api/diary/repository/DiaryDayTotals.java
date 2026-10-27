package com.estebanmm13.pytra_api.diary.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Every diary line of one day together: its hours and how many games were played (one line per game). */
public record DiaryDayTotals(LocalDate date, BigDecimal hours, Long gameCount) {
}
