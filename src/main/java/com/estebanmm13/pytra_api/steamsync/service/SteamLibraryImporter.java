package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.steamsync.client.SteamOwnedGame;
import com.estebanmm13.pytra_api.steamsync.client.SteamOwnedGamesResult;
import com.estebanmm13.pytra_api.steamsync.dto.SteamSyncResultDto;
import com.estebanmm13.pytra_api.steamsync.repository.SteamIgnoredAppRepository;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applies an already-fetched Steam library to the database. The HTTP call to Steam happens
 * BEFORE this runs, so no transaction (nor DB connection) is held while waiting on Steam.
 *
 * <p>Two phases:
 * <ol>
 *   <li>One transaction updates the apps that already have a STEAM link (hour deltas). Those only
 *       touch existing rows, so they cannot hit a unique constraint.</li>
 *   <li>Each app WITHOUT a link gets its own short transaction: attach it to the user's game with
 *       the same name, or create a pending placeholder. Inserts are where constraints can bite
 *       (unique user+lower(name), one STEAM link per game), and with Postgres a failed statement
 *       poisons the whole transaction — isolating them means one bad app is counted as
 *       {@code errored} instead of aborting the rest of the library.</li>
 * </ol>
 */
@Slf4j
@Component
public class SteamLibraryImporter {

    /** games.name is VARCHAR(255). */
    private static final int MAX_NAME_LENGTH = 255;

    private final SteamLinkRepository steamLinkRepository;
    private final GameRepository gameRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final SteamIgnoredAppRepository steamIgnoredAppRepository;
    private final SteamPlaytimeWriter steamPlaytimeWriter;
    private final TransactionTemplate transactionTemplate;

    public SteamLibraryImporter(SteamLinkRepository steamLinkRepository,
                                GameRepository gameRepository,
                                GamePlatformLinkRepository gamePlatformLinkRepository,
                                SteamIgnoredAppRepository steamIgnoredAppRepository,
                                SteamPlaytimeWriter steamPlaytimeWriter,
                                PlatformTransactionManager transactionManager) {
        this.steamLinkRepository = steamLinkRepository;
        this.gameRepository = gameRepository;
        this.gamePlatformLinkRepository = gamePlatformLinkRepository;
        this.steamIgnoredAppRepository = steamIgnoredAppRepository;
        this.steamPlaytimeWriter = steamPlaytimeWriter;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public SteamSyncResultDto importLibrary(Long userId, String steamId, SteamOwnedGamesResult library) {
        if (library.profilePrivate()) {
            return SteamSyncResultDto.privateProfile();
        }

        Counters counters = new Counters();
        counters.scanned = library.games().size();

        LinkedPhaseResult linkedPhase = transactionTemplate.execute(
                status -> updateLinkedApps(userId, steamId, library.games(), counters));

        for (SteamOwnedGame app : linkedPhase.unlinkedApps()) {
            try {
                Attachment attachment = transactionTemplate.execute(status -> {
                    // Re-checked per app: if the account was unlinked/switched mid-import, stop
                    // instead of attaching the old library to the new link.
                    requireStillLinked(userId, steamId);
                    return attachOrCreate(userId, app, linkedPhase.index());
                });
                // Index updated only after the commit, so a rolled-back app leaves no trace in it.
                linkedPhase.index().remember(attachment);
                counters.record(attachment.outcome());
            } catch (SteamIntegrationException e) {
                throw e;
            } catch (RuntimeException e) {
                counters.errored++;
                // Class name only: driver messages can echo the offending values.
                log.warn("Steam import of appid {} for userId {} failed: {}",
                        app.appId(), userId, e.getClass().getSimpleName());
            }
        }

        return counters.toDto();
    }

    // ---- Phase 1: apps that already have a link -------------------------------------------

    private LinkedPhaseResult updateLinkedApps(Long userId, String steamId, List<SteamOwnedGame> apps, Counters counters) {
        // The link may have been removed or switched to another account while we were calling Steam.
        // Conditional bulk UPDATE, not entity write: see SteamLinkRepository#markSynced.
        LocalDateTime now = LocalDateTime.now();
        if (steamLinkRepository.markSynced(userId, steamId, now) == 0) {
            throw SteamIntegrationException.linkChanged();
        }

        Map<String, GamePlatformLink> linksByAppId = gamePlatformLinkRepository
                .findAllByUserIdAndPlatformWithGame(userId, ExternalPlatform.STEAM).stream()
                .collect(Collectors.toMap(GamePlatformLink::getExternalId, Function.identity(), (a, b) -> a));
        Set<String> ignoredAppIds = steamIgnoredAppRepository.findAppIdsByUserId(userId);

        List<SteamOwnedGame> unlinked = new ArrayList<>();
        for (SteamOwnedGame app : apps) {
            String appId = String.valueOf(app.appId());
            GamePlatformLink link = linksByAppId.get(appId);
            if (link == null) {
                if (ignoredAppIds.contains(appId)) {
                    counters.ignored++;
                } else {
                    unlinked.add(app);
                }
                continue;
            }
            if (applyPlaytime(link, app.playtimeForeverMinutes(), now)) {
                counters.updated++;
            }
        }

        return new LinkedPhaseResult(unlinked, buildNameIndex(userId, linksByAppId.values()));
    }

    /**
     * Moves the link's baseline to Steam's current total. Managed entities are dirty-checked,
     * so a link whose playtime did not change is not written at all.
     *
     * @return true when hours were added to a confirmed game
     */
    private boolean applyPlaytime(GamePlatformLink link, long currentMinutes, LocalDateTime now) {
        long baselineMinutes = link.getLastSyncedPlaytimeMinutes();
        if (currentMinutes == baselineMinutes) {
            return false;
        }

        link.setLastSyncedPlaytimeMinutes(currentMinutes);
        link.setLastSyncedAt(now);

        // Negative delta (refund, lost family-sharing access, Steam-side reset): only move the
        // baseline down. Never subtract from the user's data, and never re-add the old hours later.
        if (currentMinutes < baselineMinutes) {
            return false;
        }

        // PENDING_REVIEW games only track the baseline: confirm imports the full total at once.
        Game game = link.getGame();
        if (game.getReviewStatus() != ReviewStatus.CONFIRMED) {
            return false;
        }
        return steamPlaytimeWriter.addDelta(link, game, (currentMinutes - baselineMinutes) / 60.0, now);
    }

    private void requireStillLinked(Long userId, String steamId) {
        if (!steamLinkRepository.isLinkedTo(userId, steamId)) {
            throw SteamIntegrationException.linkChanged();
        }
    }

    private NameIndex buildNameIndex(Long userId, Iterable<GamePlatformLink> steamLinks) {
        Map<String, Long> gameIdByName = new HashMap<>();
        for (Game game : gameRepository.findAllByUserId(userId)) {
            gameIdByName.putIfAbsent(nameKey(game.getName()), game.getId());
        }
        Set<Long> gameIdsWithSteamLink = new HashSet<>();
        for (GamePlatformLink link : steamLinks) {
            gameIdsWithSteamLink.add(link.getGame().getId());
        }
        return new NameIndex(gameIdByName, gameIdsWithSteamLink);
    }

    // ---- Phase 2: one transaction per app without a link ----------------------------------

    private Attachment attachOrCreate(Long userId, SteamOwnedGame app, NameIndex index) {
        String appId = String.valueOf(app.appId());
        String name = truncate(app.name());
        String key = nameKey(name);
        LocalDateTime now = LocalDateTime.now();

        Long existingGameId = index.gameIdByName().get(key);
        if (existingGameId != null) {
            if (index.gameIdsWithSteamLink().contains(existingGameId)) {
                // Another Steam app already owns that game (duplicate names, DLC/test apps).
                return new Attachment(Outcome.SKIPPED, null, key);
            }
            // The user already tracks this game by hand. Its hours were entered manually and very
            // likely include (part of) the Steam total, so importing that total would double count:
            // the current Steam playtime becomes the baseline and only future deltas are added.
            gamePlatformLinkRepository.save(GamePlatformLink.builder()
                    .userId(userId)
                    .game(gameRepository.getReferenceById(existingGameId))
                    .platform(ExternalPlatform.STEAM)
                    .externalId(appId)
                    .lastSyncedPlaytimeMinutes(app.playtimeForeverMinutes())
                    .lastSyncedAt(now)
                    .build());
            return new Attachment(Outcome.LINKED_EXISTING, existingGameId, key);
        }

        Game game = gameRepository.save(Game.builder()
                .userId(userId)
                .name(name)
                .category(null)
                .reviewStatus(ReviewStatus.PENDING_REVIEW)
                .genres(new HashSet<>())
                .build());
        gamePlatformLinkRepository.save(GamePlatformLink.builder()
                .userId(userId)
                .game(game)
                .platform(ExternalPlatform.STEAM)
                .externalId(appId)
                .lastSyncedPlaytimeMinutes(app.playtimeForeverMinutes())
                .lastSyncedAt(now)
                .build());
        return new Attachment(Outcome.CREATED_PENDING, game.getId(), key);
    }

    private static String truncate(String name) {
        String safe = (name == null || name.isBlank()) ? "Unknown" : name.strip();
        return safe.length() <= MAX_NAME_LENGTH ? safe : safe.substring(0, MAX_NAME_LENGTH);
    }

    /** Mirrors the unique index on (user_id, LOWER(name)). */
    private static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // ---- Bookkeeping ---------------------------------------------------------------------

    private enum Outcome { CREATED_PENDING, LINKED_EXISTING, SKIPPED }

    private record Attachment(Outcome outcome, Long gameId, String nameKey) {
    }

    private record NameIndex(Map<String, Long> gameIdByName, Set<Long> gameIdsWithSteamLink) {
        void remember(Attachment attachment) {
            if (attachment.gameId() == null) return;
            gameIdByName.putIfAbsent(attachment.nameKey(), attachment.gameId());
            gameIdsWithSteamLink.add(attachment.gameId());
        }
    }

    private record LinkedPhaseResult(List<SteamOwnedGame> unlinkedApps, NameIndex index) {
    }

    private static final class Counters {
        int scanned;
        int newPending;
        int linkedExisting;
        int updated;
        int ignored;
        int skipped;
        int errored;

        void record(Outcome outcome) {
            switch (outcome) {
                case CREATED_PENDING -> newPending++;
                case LINKED_EXISTING -> linkedExisting++;
                case SKIPPED -> skipped++;
            }
        }

        SteamSyncResultDto toDto() {
            return new SteamSyncResultDto(scanned, newPending, linkedExisting, updated, ignored, skipped, errored, false);
        }
    }
}
