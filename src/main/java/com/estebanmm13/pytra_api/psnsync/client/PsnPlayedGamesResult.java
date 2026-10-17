package com.estebanmm13.pytra_api.psnsync.client;

import java.util.List;

/**
 * Result of the PSN game list. {@code profilePrivate} is true when Sony refused to show the list
 * (the user's "Gaming | Media" privacy is not set to "Anyone").
 */
public record PsnPlayedGamesResult(boolean profilePrivate, List<PsnPlayedTitle> titles) {

    public static PsnPlayedGamesResult privateProfile() {
        return new PsnPlayedGamesResult(true, List.of());
    }
}
