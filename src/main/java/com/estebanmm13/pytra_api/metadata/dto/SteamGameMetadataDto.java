package com.estebanmm13.pytra_api.metadata.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Game metadata from the Steam store, normalized to the shape of the game form. Nothing is
 * stored: the client uses it to prefill the form and the user reviews it before saving.
 *
 * @param developer     first developer listed by Steam; null when missing
 * @param publisher     first publisher listed by Steam; null when missing
 * @param releaseDate   null when the game is not released yet or the date can't be parsed
 * @param genres        Steam genre names as localized by the store (Spanish), never null
 * @param coverImageUrl the store header image (https); null when missing
 */
public record SteamGameMetadataDto(
        long steamAppId,
        String name,
        String developer,
        String publisher,
        LocalDate releaseDate,
        List<String> genres,
        String coverImageUrl
) {
}
