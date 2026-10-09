package com.estebanmm13.pytra_api.steamsync.dto;

import java.time.LocalDateTime;

/** {@code configured} is false when the server has no STEAM_API_KEY: linking works, syncing does not. */
public record SteamStatusDto(
        boolean linked,
        String steamId,
        String personaName,
        LocalDateTime lastSyncAt,
        boolean configured
) {
}
