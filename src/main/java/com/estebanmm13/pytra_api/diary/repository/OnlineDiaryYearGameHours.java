package com.estebanmm13.pytra_api.diary.repository;

import java.math.BigDecimal;

/** Diary hours of one game in one year that were counted into its online playtime. */
public record OnlineDiaryYearGameHours(Integer year, Long gameId, BigDecimal hours) {
}
