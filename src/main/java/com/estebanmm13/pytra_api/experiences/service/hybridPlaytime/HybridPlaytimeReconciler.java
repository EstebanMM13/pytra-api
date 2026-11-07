package com.estebanmm13.pytra_api.experiences.service.hybridPlaytime;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlatformHours;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.platformsync.ExternalPlaytimeWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Keeps a confirmed HYBRID game's OnlinePlaytime in step with its synced share
 * ({@link SyncedOnlineHours}) when the synced totals or the runs change. Must run inside the caller's
 * transaction.
 *
 * <p>OnlinePlaytime is one number per game, with no record of which part came from a sync and which
 * part the user typed for play the platforms cannot see (another console, offline, before linking).
 * So the synced share is never written as the total: only the CHANGE of the share is applied
 * ({@code online += share after - share before}). Whatever the user typed on top of the share is kept,
 * and with no runs on linked platforms this is exactly the old "add positive deltas" behaviour.
 * The result is clamped at 0.
 */
@Component
@RequiredArgsConstructor
public class HybridPlaytimeReconciler {

    private static final double EPSILON = 1e-9;

    private final ExperienceRepository experienceRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final com.estebanmm13.pytra_api.platforms.service.PlatformService platformService;

    /**
     * Synced share before a run of {@code game} is created, edited or deleted; pass it to
     * {@link #afterRunChange}. Null when the game is not a confirmed HYBRID game with platform links.
     */
    public SyncedOnlineHours.Share shareBeforeRunChange(Game game) {
        if (!isConfirmedHybrid(game)) {
            return null;
        }
        List<GamePlatformLink> links = gamePlatformLinkRepository.findAllByUserIdAndGameId(game.getUserId(), game.getId());
        if (links.isEmpty()) {
            return null;
        }
        return SyncedOnlineHours.share(links, runsOf(game));
    }

    /**
     * Moves the online hours by the change of the synced share caused by a run change, source by source,
     * in the total and in the rows of that source's platforms (a PC run never moves PlayStation hours).
     */
    public void afterRunChange(Game game, SyncedOnlineHours.Share shareBefore) {
        if (shareBefore == null) {
            return;
        }
        // The run change must be visible to the query below.
        experienceRepository.flush();
        List<GamePlatformLink> links = gamePlatformLinkRepository.findAllByUserIdAndGameId(game.getUserId(), game.getId());
        SyncedOnlineHours.Share shareAfter = SyncedOnlineHours.share(links, runsOf(game));
        for (SyncedOnlineHours.Source source : SyncedOnlineHours.Source.values()) {
            List<Platform> platforms = platformsOf(links, source);
            shiftOnlineHours(game, shareBefore.of(source), shareAfter.of(source), null,
                    (onlinePlaytime, delta) -> moveSourceRows(onlinePlaytime, platforms, delta));
        }
    }

    /**
     * Catalog platforms of a source's links, the most synced first. Several when PSN links a PS4 and a
     * PS5 title of the game.
     */
    private List<Platform> platformsOf(List<GamePlatformLink> links, SyncedOnlineHours.Source source) {
        Map<Long, Platform> platforms = new LinkedHashMap<>();
        links.stream()
                .filter(link -> SyncedOnlineHours.sourceOf(link.getPlatform()) == source)
                .sorted(Comparator.comparingLong(GamePlatformLink::getLastSyncedPlaytimeMinutes).reversed())
                .map(link -> ExternalPlaytimeWriter.catalogPlatformOf(link, platformService))
                .filter(platform -> platform != null && platform.getId() != null)
                .forEach(platform -> platforms.putIfAbsent(platform.getId(), platform));
        return new ArrayList<>(platforms.values());
    }

    /**
     * Rows of one source after its share moved by {@code delta}. Hours given back go to its most synced
     * platform. Hours taken out come from its largest rows first: the run's console is not always the one
     * holding the hours (a PS4 title played on a PS5). Rows never go below 0.
     */
    private static void moveSourceRows(OnlinePlaytime onlinePlaytime, List<Platform> platforms, double delta) {
        if (platforms.isEmpty()) {
            return;
        }
        if (delta > 0) {
            OnlinePlatformHours.add(onlinePlaytime, platforms.get(0), delta);
            return;
        }
        double remaining = -delta;
        List<Platform> largestFirst = new ArrayList<>(platforms);
        largestFirst.sort(Comparator.comparingDouble(
                (Platform platform) -> onlinePlaytime.getPlatformHours().getOrDefault(platform.getId(), 0.0)).reversed());
        for (Platform platform : largestFirst) {
            double taken = Math.min(remaining, onlinePlaytime.getPlatformHours().getOrDefault(platform.getId(), 0.0));
            if (taken > 0) {
                OnlinePlatformHours.add(onlinePlaytime, platform, -taken);
                remaining -= taken;
            }
            if (remaining <= EPSILON) {
                return;
            }
        }
    }

    /** Synced share of a HYBRID game for the given links, with the game's current runs. */
    public double shareOf(Game game, Collection<GamePlatformLink> links) {
        return SyncedOnlineHours.of(links, runsOf(game));
    }

    /**
     * A sync moved {@code link} from {@code previousMinutes} to its current total. Applies the change of
     * the game's synced share (every link of the game counts: Steam and PSN can both link it).
     *
     * @return true when the online hours went up
     */
    public boolean applySyncedChange(GamePlatformLink link, long previousMinutes, LocalDateTime sessionAt) {
        Game game = link.getGame();
        List<GamePlatformLink> after = new ArrayList<>();
        List<GamePlatformLink> before = new ArrayList<>();
        for (GamePlatformLink other : gamePlatformLinkRepository.findAllByUserIdAndGameId(link.getUserId(), game.getId())) {
            if (other == link || (other.getId() != null && other.getId().equals(link.getId()))) {
                continue;
            }
            after.add(other);
            before.add(other);
        }
        after.add(link);
        // Detached copy: the managed link already holds the new total and must not be touched.
        before.add(GamePlatformLink.builder()
                .platform(link.getPlatform())
                .lastSyncedPlaytimeMinutes(previousMinutes)
                .build());

        List<Experience> runs = runsOf(game);
        double shareBefore = SyncedOnlineHours.of(before, runs);
        double shareAfter = SyncedOnlineHours.of(after, runs);
        Platform platform = ExternalPlaytimeWriter.catalogPlatformOf(link, platformService);
        return shiftOnlineHours(game, shareBefore, shareAfter, sessionAt,
                (onlinePlaytime, delta) -> OnlinePlatformHours.add(onlinePlaytime, platform, delta))
                && shareAfter > shareBefore;
    }

    /** {@code moveRows}: moves the per-platform breakdown by the same change as the total. */
    private boolean shiftOnlineHours(Game game, double shareBefore, double shareAfter, LocalDateTime sessionAt,
                                     BiConsumer<OnlinePlaytime, Double> moveRows) {
        double delta = shareAfter - shareBefore;
        if (Math.abs(delta) < EPSILON) {
            return false;
        }
        OnlinePlaytime onlinePlaytime = onlinePlaytimeRepository.findByGameIdAndUserId(game.getId(), game.getUserId())
                .orElse(null);
        if (onlinePlaytime == null) {
            // Nothing to take hours from (the user deleted the record): leave it deleted.
            if (delta < 0) {
                return false;
            }
            onlinePlaytime = OnlinePlaytime.builder().userId(game.getUserId()).gameId(game.getId()).totalHours(0.0).build();
        }
        onlinePlaytime.setTotalHours(Math.max(0.0, onlinePlaytime.getTotalHours() + delta));
        moveRows.accept(onlinePlaytime, delta);
        if (sessionAt != null) {
            onlinePlaytime.setLastSessionAt(sessionAt);
        }
        onlinePlaytimeRepository.save(onlinePlaytime);
        return true;
    }

    private List<Experience> runsOf(Game game) {
        return experienceRepository.findAllByGameIdAndUserId(game.getId(), game.getUserId());
    }

    private static boolean isConfirmedHybrid(Game game) {
        return game != null
                && game.getCategory() == GameCategory.HYBRID
                && game.getReviewStatus() == ReviewStatus.CONFIRMED;
    }
}
