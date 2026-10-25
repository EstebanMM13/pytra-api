package com.estebanmm13.pytra_api.diary.repository;

import java.math.BigDecimal;

/** Diary hours of one year that were counted into online playtime (all games together). */
public record OnlineDiaryYearHours(Integer year, BigDecimal hours) {
}
