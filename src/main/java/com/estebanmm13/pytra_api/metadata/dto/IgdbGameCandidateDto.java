package com.estebanmm13.pytra_api.metadata.dto;

import java.util.Set;

/**
 * One hit of a platform-filtered IGDB search, with its full metadata, so a confident match can be
 * applied without a second request. Internal: never returned by an endpoint.
 *
 * @param metadata    the same normalized details a details query returns
 * @param platformIds IGDB platform ids the game was released on (e.g. 6 PC, 48 PS4, 167 PS5), never null
 * @param gameModeIds IGDB game mode ids (1 single player, 2 multiplayer, 3 co-operative, 4 split screen,
 *                    5 MMO, 6 battle royale), never null
 */
public record IgdbGameCandidateDto(IgdbGameMetadataDto metadata, Set<Integer> platformIds, Set<Integer> gameModeIds) {
}
