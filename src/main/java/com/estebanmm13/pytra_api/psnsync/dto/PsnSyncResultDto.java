package com.estebanmm13.pytra_api.psnsync.dto;

/**
 * Outcome of one PSN library sync (same shape as SteamSyncResultDto).
 *
 * @param gamesScanned    PS4/PS5 titles PSN reported for the account
 * @param newGamesPending new placeholder games created in PENDING_REVIEW
 * @param linkedExisting  titles attached to a game the user already had (same name, case-insensitive)
 * @param gamesUpdated    confirmed games whose hours grew since the previous sync
 * @param ignored         titles skipped because the user ignored them
 * @param skipped         titles that could not be matched safely (the same-name game already has a link
 *                        for that console, e.g. two regional versions)
 * @param errored         titles whose import failed; the rest of the library was still synced
 * @param profilePrivate  PSN hid the game list (privacy "Gaming | Media" not set to "Anyone"); nothing was changed
 */
public record PsnSyncResultDto(
        int gamesScanned,
        int newGamesPending,
        int linkedExisting,
        int gamesUpdated,
        int ignored,
        int skipped,
        int errored,
        boolean profilePrivate
) {
    public static PsnSyncResultDto privateProfile() {
        return new PsnSyncResultDto(0, 0, 0, 0, 0, 0, 0, true);
    }
}
