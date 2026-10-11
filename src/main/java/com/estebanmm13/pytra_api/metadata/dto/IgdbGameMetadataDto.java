package com.estebanmm13.pytra_api.metadata.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Game metadata from IGDB, normalized to the same shape as {@link SteamGameMetadataDto} so the
 * client fills the game form the same way. Nothing is stored.
 *
 * @param developer     first company IGDB flags as developer; null when missing
 * @param publisher     first company IGDB flags as publisher; null when missing
 * @param releaseDate   first release date (UTC); null when unknown or in the future
 * @param genres        IGDB genre names (English, e.g. "Role-playing (RPG)"), never null
 * @param coverImageUrl landscape artwork when IGDB has one, the portrait cover otherwise (https); null when neither
 * @param platforms     full platform names, never null
 */
public record IgdbGameMetadataDto(
        long igdbId,
        String name,
        String developer,
        String publisher,
        LocalDate releaseDate,
        List<String> genres,
        String coverImageUrl,
        List<String> platforms
) {
}
