package com.estebanmm13.pytra_api.games.repository;

import com.estebanmm13.pytra_api.games.model.GameCategory;

import java.time.LocalDateTime;

/** A game's category, when it was added to the library and its backlog flag, read without loading the Game entity. */
public record GameAddedRow(Long gameId, GameCategory category, LocalDateTime createdAt, Boolean backlog) {
}
