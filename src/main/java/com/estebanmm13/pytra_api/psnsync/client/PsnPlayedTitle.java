package com.estebanmm13.pytra_api.psnsync.client;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;

import java.time.Instant;

/**
 * One PS4/PS5 title from the user's PSN game list.
 *
 * @param titleId         PSN title id (CUSAxxxxx_00 for PS4, PPSAxxxxx_00 for PS5); stored as the link's external id
 * @param platform        {@link ExternalPlatform#PSN_PS4} or {@link ExternalPlatform#PSN_PS5}, from the title category
 * @param playtimeMinutes {@code playDuration} rounded to the nearest minute (0 when missing or malformed)
 * @param firstPlayedAt   {@code firstPlayedDateTime}; null when missing. Not persisted in phase 1
 * @param lastPlayedAt    {@code lastPlayedDateTime}; null when missing
 */
public record PsnPlayedTitle(
        String titleId,
        String name,
        String imageUrl,
        ExternalPlatform platform,
        int playCount,
        long playtimeMinutes,
        Instant firstPlayedAt,
        Instant lastPlayedAt
) {
}
