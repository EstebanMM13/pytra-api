package com.estebanmm13.pytra_api.stats.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A CONFIRMED game with its total hours (runs + online) and its rating (null when unrated). */
record GameFacts(Long gameId, String developer, String publisher, LocalDate releaseDate, double hours, BigDecimal rating) {
}
