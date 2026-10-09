package com.estebanmm13.pytra_api.steamsync.dto;

/**
 * Outcome of one library sync.
 *
 * @param gamesScanned    games Steam reported for the account
 * @param newGamesPending new placeholder games created in PENDING_REVIEW
 * @param linkedExisting  Steam apps attached to a game the user already had (same name, case-insensitive)
 * @param gamesUpdated    confirmed games whose hours grew since the previous sync
 * @param ignored         apps skipped because the user ignored them
 * @param skipped         apps that could not be matched safely (e.g. the same-name game is already linked to another app)
 * @param errored         apps whose import failed; the rest of the library was still synced
 * @param profilePrivate  Steam hid the library (private profile or private game details); nothing was changed
 */
public record SteamSyncResultDto(
        int gamesScanned,
        int newGamesPending,
        int linkedExisting,
        int gamesUpdated,
        int ignored,
        int skipped,
        int errored,
        boolean profilePrivate
) {
    public static SteamSyncResultDto privateProfile() {
        return new SteamSyncResultDto(0, 0, 0, 0, 0, 0, 0, true);
    }
}
