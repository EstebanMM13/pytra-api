package com.estebanmm13.pytra_api.steamsync.dto;

import java.time.LocalDateTime;

/**
 * {@code configured} is false when the server has no STEAM_API_KEY: linking works, syncing does not.
 *
 * @param linkedGamesCount STEAM platform links on the user's games (pending placeholders included)
 * @param pendingCount     games still pending review
 * @param ignoredCount     Steam apps the user ignored
 */
public record SteamStatusDto(
        boolean linked,
        String steamId,
        String personaName,
        LocalDateTime lastSyncAt,
        boolean configured,
        long linkedGamesCount,
        long pendingCount,
        long ignoredCount
) {
}
