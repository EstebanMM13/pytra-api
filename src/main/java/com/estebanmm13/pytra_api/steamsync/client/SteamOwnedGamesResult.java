package com.estebanmm13.pytra_api.steamsync.client;

import java.util.List;

/**
 * Result of GetOwnedGames. {@code profilePrivate} is true when Steam answered without a
 * {@code game_count} (it does that, with HTTP 200, when the profile or its game details are private).
 */
public record SteamOwnedGamesResult(boolean profilePrivate, List<SteamOwnedGame> games) {

    public static SteamOwnedGamesResult privateProfile() {
        return new SteamOwnedGamesResult(true, List.of());
    }
}
