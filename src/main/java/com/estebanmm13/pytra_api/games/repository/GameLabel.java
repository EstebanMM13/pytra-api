package com.estebanmm13.pytra_api.games.repository;

/** Just what the stats need to label a row with its game; read without loading the Game entity. */
public record GameLabel(Long id, String name, String coverImageUrl) {
}
