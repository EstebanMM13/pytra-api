package com.estebanmm13.pytra_api.experiences.service.hybridPlaytime;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Part of a HYBRID game's synced hours that belongs to OnlinePlaytime.
 *
 * <p>Steam and PSN report ONE cumulative total per title, which already includes any run the user
 * logged on that platform. Counting the run AND the whole synced total as online hours would count
 * those hours twice, so runs on a platform covered by the links are taken out of the synced total:
 * {@code online share = sum over sources of max(0, synced hours of the source - run hours on its platforms)}.
 *
 * <p>Sources are kept apart so a long PC run never eats PlayStation hours. PSN links (PS4 and PS5
 * versions) form one source covering both consoles: a PS4 version can be played on a PS5, and the run's
 * platform is the console it was played on. Runs are matched through the seeded catalog entries
 * ({@code platforms.legacy_code}); runs on user-created platforms are not covered by any link.
 */
public final class SyncedOnlineHours {

    private enum Source { STEAM, PSN }

    private static final Map<Source, Set<String>> COVERED_PLATFORMS = Map.of(
            Source.STEAM, Set.of("PC"),
            Source.PSN, Set.of("PS4", "PS5"));

    private SyncedOnlineHours() {
    }

    /** Online share of the given links and runs, never negative. */
    public static double of(Collection<GamePlatformLink> links, Collection<Experience> runs) {
        double share = 0.0;
        for (Source source : Source.values()) {
            long syncedMinutes = links.stream()
                    .filter(link -> sourceOf(link.getPlatform()) == source)
                    .mapToLong(GamePlatformLink::getLastSyncedPlaytimeMinutes)
                    .sum();
            if (syncedMinutes == 0) {
                continue;
            }
            double runHours = runs.stream()
                    .filter(run -> isOn(run, COVERED_PLATFORMS.get(source)))
                    .mapToDouble(run -> run.getHours() != null ? run.getHours() : 0.0)
                    .sum();
            share += Math.max(0.0, syncedMinutes / 60.0 - runHours);
        }
        return share;
    }

    private static Source sourceOf(ExternalPlatform platform) {
        if (platform == null) return null;
        return switch (platform) {
            case STEAM -> Source.STEAM;
            case PSN_PS4, PSN_PS5 -> Source.PSN;
            // Never written by any sync: no hours to share.
            default -> null;
        };
    }

    private static boolean isOn(Experience run, Set<String> legacyCodes) {
        return run.getPlatform() != null
                && run.getPlatform().getLegacyCode() != null
                && legacyCodes.contains(run.getPlatform().getLegacyCode().toUpperCase());
    }
}
