package com.estebanmm13.pytra_api.games.repository;

/** One row per (game, genre) pair; games without genres produce no row. */
public record GameGenreRow(Long gameId, Long genreId, String genreName) {
}
