package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameCandidateDto;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static com.estebanmm13.pytra_api.platformsync.ImportedGameNames.nameKey;

/**
 * Decides whether an IGDB search hit is safe to fill a synced pending game with. Pure.
 *
 * <p>A match is CONFIDENT only when exactly one IGDB game has the same {@link ImportedGameNames#nameKey
 * name key} as the pending game AND was released on one of the platforms it was synced from. Anything
 * else (no hit, a near name, two games with that name on that platform) is ambiguous and left alone,
 * except when the date the user last played it rules out all but one of those games (see
 * {@link #select(String, Set, List, LocalDate)}).
 */
public final class IgdbPendingMatch {

    static final int IGDB_PC = 6;
    static final int IGDB_PS4 = 48;
    static final int IGDB_PS5 = 167;

    static final int MODE_SINGLE_PLAYER = 1;
    /** Multiplayer, co-operative, split screen, MMO, battle royale. */
    private static final Set<Integer> MULTIPLAYER_MODES = Set.of(2, 3, 4, 5, 6);

    private IgdbPendingMatch() {
    }

    /**
     * IGDB platform ids for the link platforms of a pending game: Steam is PC; a PSN title is its console
     * (PS4 or PS5), or both when the console is unknown (legacy PSN links). Empty when no link maps.
     */
    public static Set<Integer> igdbPlatformIds(Collection<ExternalPlatform> linkPlatforms) {
        Set<Integer> ids = new TreeSet<>();
        for (ExternalPlatform platform : linkPlatforms) {
            switch (platform) {
                case STEAM -> ids.add(IGDB_PC);
                case PSN_PS4 -> ids.add(IGDB_PS4);
                case PSN_PS5 -> ids.add(IGDB_PS5);
                case PSN -> {
                    ids.add(IGDB_PS4);
                    ids.add(IGDB_PS5);
                }
                case XBOX, EPIC, MANUAL -> {
                    // Not synced: never seen on a pending game, and no reliable IGDB platform to filter on.
                }
            }
        }
        return ids;
    }

    /** The single confident candidate, or empty when there is none or more than one. */
    public static Optional<IgdbGameCandidateDto> select(String gameName, Set<Integer> platformIds,
                                                        List<IgdbGameCandidateDto> candidates) {
        return select(gameName, platformIds, candidates, null);
    }

    /**
     * Same as {@link #select(String, Set, List)}, but when several same-name games remain, the ones released
     * more than a day after {@code lastPlayedOn} are dropped: the user cannot have played a game before it
     * came out (e.g. a 2024 remake for a title last played in 2016). Confident only if exactly one is left and
     * its release date is known.
     *
     * @param lastPlayedOn the latest day the platforms report the game was played; null when unknown
     */
    public static Optional<IgdbGameCandidateDto> select(String gameName, Set<Integer> platformIds,
                                                        List<IgdbGameCandidateDto> candidates, LocalDate lastPlayedOn) {
        String key = gameName == null ? "" : nameKey(gameName);
        if (key.isEmpty() || platformIds.isEmpty()) return Optional.empty();

        List<IgdbGameCandidateDto> confident = candidates.stream()
                .filter(c -> key.equals(nameKey(c.metadata().name())))
                .filter(c -> !Collections.disjoint(c.platformIds(), platformIds))
                .toList();
        if (distinctGames(confident) > 1 && lastPlayedOn != null) {
            // One day of slack: IGDB dates are days (UTC), the platform's play time an instant.
            LocalDate latestPossibleRelease = lastPlayedOn.plusDays(1);
            // An undated game might be the one, so it stays in the race but never wins it alone.
            List<IgdbGameCandidateDto> possible = confident.stream()
                    .filter(c -> c.metadata().releaseDate() == null
                            || !c.metadata().releaseDate().isAfter(latestPossibleRelease))
                    .toList();
            boolean provable = distinctGames(possible) == 1 && possible.getFirst().metadata().releaseDate() != null;
            return provable ? Optional.of(possible.getFirst()) : Optional.empty();
        }
        return distinctGames(confident) == 1 ? Optional.of(confident.getFirst()) : Optional.empty();
    }

    private static long distinctGames(List<IgdbGameCandidateDto> candidates) {
        return candidates.stream().mapToLong(c -> c.metadata().igdbId()).distinct().count();
    }

    /**
     * Category suggested by IGDB's game modes: single player only is SINGLEPLAYER, multiplayer modes
     * only is ONLINE, both is HYBRID; null when IGDB lists no mode.
     */
    public static GameCategory suggestCategory(Set<Integer> gameModeIds) {
        boolean single = gameModeIds.contains(MODE_SINGLE_PLAYER);
        boolean multi = !Collections.disjoint(gameModeIds, MULTIPLAYER_MODES);
        if (single && multi) return GameCategory.HYBRID;
        if (multi) return GameCategory.ONLINE;
        if (single) return GameCategory.SINGLEPLAYER;
        return null;
    }
}
