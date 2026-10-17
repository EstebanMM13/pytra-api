package com.estebanmm13.pytra_api.psnsync.service;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.platformsync.ImportedRunSource;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

/** The link platforms the PSN sync owns. */
public final class PsnPlatforms {

    public static final Set<ExternalPlatform> ALL = Set.of(ExternalPlatform.PSN_PS5, ExternalPlatform.PSN_PS4);

    /** Every other link platform (Steam...): a game they link is not the PSN sync's to delete. */
    public static final Set<ExternalPlatform> OTHERS = Set.copyOf(EnumSet.complementOf(EnumSet.copyOf(ALL)));

    /** PS5 first: when a game has both titles, the PS5 one represents it. */
    public static final Comparator<ExternalPlatform> PS5_FIRST =
            Comparator.comparing(platform -> platform == ExternalPlatform.PSN_PS5 ? 0 : 1);

    private PsnPlatforms() {
    }

    /** Catalog legacy code of the console ("PS5" / "PS4"), as exposed to clients. */
    public static String consoleCode(ExternalPlatform platform) {
        return ImportedRunSource.of(platform).platformLegacyCode();
    }
}
