package com.estebanmm13.pytra_api.experiences.repository;

import java.math.BigDecimal;

/** A game's online playtime hours and general rating (may be null), read without loading the entity. */
public record OnlinePlaytimeRow(Long gameId, Double totalHours, BigDecimal generalRating) {
}
