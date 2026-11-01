package com.estebanmm13.pytra_api.diary.repository;

/** Distinct diary days on which a game was logged. */
public record GameDiaryDays(Long gameId, Long days) {
}
