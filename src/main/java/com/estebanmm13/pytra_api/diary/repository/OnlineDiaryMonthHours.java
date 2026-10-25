package com.estebanmm13.pytra_api.diary.repository;

import java.math.BigDecimal;

/** Diary hours of one game in one month that were counted into its online playtime, and on how many days. */
public record OnlineDiaryMonthHours(Integer month, Long gameId, BigDecimal hours, Long dayCount) {
}
