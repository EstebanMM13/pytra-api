package com.estebanmm13.pytra_api.games.repository;

import java.time.LocalDate;

/** A game's developer (free text, may be null) and release date, read without loading the Game entity. */
public record GameReleaseRow(Long gameId, String developer, LocalDate releaseDate) {
}
