package com.estebanmm13.pytra_api.diary.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Diary hours of one day added to one run (lines that count hours and point at the run). */
public record RunDiaryDayHours(Long experienceId, LocalDate day, BigDecimal hours) {
}
