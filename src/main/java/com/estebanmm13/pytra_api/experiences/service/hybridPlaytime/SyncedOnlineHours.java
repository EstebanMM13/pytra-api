package com.estebanmm13.pytra_api.experiences.service.hybridPlaytime;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;

import java.util.Collection;
import java.util.EnumMap;
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

    /** A sync that reports one cumulative total per title. */
    public enum Source { STEAM, PSN, ANDROID }

    /** Online share of each source; a source with nothing synced is absent. */
    public record Share(Map<Source, Double> bySource) {

        public Share {
            bySource = Map.copyOf(bySource);
        }

        public double of(Source source) {
            return bySource.getOrDefault(source, 0.0);
        }

        public double total() {
            return bySource.values().stream().mapToDouble(Double::doubleValue).sum();
        }
    }

    private static final Map<Source, Set<String>> COVERED_PLATFORMS = Map.of(
            Source.STEAM, Set.of("PC"),
            Source.PSN, Set.of("PS4", "PS5"),
            Source.ANDROID, Set.of("MOBILE"));

    private SyncedOnlineHours() {
    }

    /** Online share of the given links and runs, never negative. */
    public static double of(Collection<GamePlatformLink> links, Collection<Experience> runs) {
        return share(links, runs).total();
    }

    /** The same share, per source: a run change moves only the platform rows of its own source. */
    public static Share share(Collection<GamePlatformLink> links, Collection<Experience> runs) {
        Map<Source, Double> bySource = new EnumMap<>(Source.class);
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
            bySource.put(source, Math.max(0.0, syncedMinutes / 60.0 - runHours));
        }
        return new Share(bySource);
    }

    static Source sourceOf(ExternalPlatform platform) {
        if (platform == null) return null;
        return switch (platform) {
            case STEAM -> Source.STEAM;
            case PSN_PS4, PSN_PS5 -> Source.PSN;
            case ANDROID -> Source.ANDROID;
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
