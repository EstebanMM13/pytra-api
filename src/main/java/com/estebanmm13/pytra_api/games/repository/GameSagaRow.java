package com.estebanmm13.pytra_api.games.repository;

/** One row per game with its saga (both saga fields null when the game has none). */
public record GameSagaRow(Long gameId, Long sagaId, String sagaName) {
}
