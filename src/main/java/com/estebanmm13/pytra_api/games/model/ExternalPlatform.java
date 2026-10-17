package com.estebanmm13.pytra_api.games.model;

/**
 * Platform a {@link GamePlatformLink} comes from. At most one link per game and platform.
 *
 * <p>PlayStation titles are split by console generation ({@link #PSN_PS4}, {@link #PSN_PS5}): PSN reports
 * the PS4 and PS5 versions of the same game as separate titles with separate playtime, and both must be
 * able to link to the same Pytra game. {@link #PSN} predates the PSN sync and is never written.
 */
public enum ExternalPlatform {
    STEAM, XBOX, PSN, EPIC, MANUAL, PSN_PS4, PSN_PS5
}
