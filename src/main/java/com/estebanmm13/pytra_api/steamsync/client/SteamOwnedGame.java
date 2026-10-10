package com.estebanmm13.pytra_api.steamsync.client;

import java.time.Instant;

/**
 * One app of the user's Steam library.
 *
 * @param lastPlayedAt from {@code rtime_last_played}; null when Steam reports 0 (never played) or omits it
 */
public record SteamOwnedGame(long appId, String name, long playtimeForeverMinutes, Instant lastPlayedAt) {

    public SteamOwnedGame(long appId, String name, long playtimeForeverMinutes) {
        this(appId, name, playtimeForeverMinutes, null);
    }
}
