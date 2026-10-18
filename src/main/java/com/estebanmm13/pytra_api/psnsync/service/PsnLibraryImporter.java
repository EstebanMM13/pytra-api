package com.estebanmm13.pytra_api.psnsync.service;

import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platformsync.ExternalPlaytimeWriter;
import com.estebanmm13.pytra_api.platformsync.PendingGameEnricher;
import com.estebanmm13.pytra_api.psnsync.client.PsnPlayedGamesResult;
import com.estebanmm13.pytra_api.psnsync.client.PsnPlayedTitle;
import com.estebanmm13.pytra_api.psnsync.dto.PsnSyncResultDto;
import com.estebanmm13.pytra_api.psnsync.repository.PsnIgnoredTitleRepository;
import com.estebanmm13.pytra_api.psnsync.repository.PsnLinkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.estebanmm13.pytra_api.platformsync.ImportedGameNames.nameKey;
import static com.estebanmm13.pytra_api.platformsync.ImportedGameNames.truncate;

/**
 * Applies an already-fetched PSN game list to the database (mirrors SteamLibraryImporter). The HTTP
 * calls to PSN happen BEFORE this runs, so no transaction (nor DB connection) is held while waiting on PSN.
 *
 * <p>Two phases:
 * <ol>
 *   <li>One transaction updates the titles that already have a PSN link (hour deltas).</li>
 *   <li>Each title WITHOUT a link gets its own short transaction: attach it to the user's game with the
 *       same name, or create a pending placeholder. One failing title is counted as {@code errored}
 *       instead of aborting the rest of the list.</li>
 * </ol>
 *
 * <p>Titles are linked per console: the PS4 and PS5 versions of a game are two PSN titles with their own
 * playtime, and both link to the same Pytra game (one PSN_PS4 and one PSN_PS5 link). A second title for
 * the same console and name (regional versions) is {@code skipped}, like Steam's duplicate names.
 */
@Slf4j
@Component
public class PsnLibraryImporter {

    /** games.cover_image_url is VARCHAR(500). */
    private static final int MAX_COVER_URL_LENGTH = 500;

    private final PsnLinkRepository psnLinkRepository;
    private final GameRepository gameRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final PsnIgnoredTitleRepository psnIgnoredTitleRepository;
    private final ExternalPlaytimeWriter externalPlaytimeWriter;
    private final PendingGameEnricher pendingGameEnricher;
    private final TransactionTemplate transactionTemplate;

    public PsnLibraryImporter(PsnLinkRepository psnLinkRepository,
                              GameRepository gameRepository,
                              GamePlatformLinkRepository gamePlatformLinkRepository,
                              PsnIgnoredTitleRepository psnIgnoredTitleRepository,
                              ExternalPlaytimeWriter externalPlaytimeWriter,
                              PendingGameEnricher pendingGameEnricher,
                              PlatformTransactionManager transactionManager) {
        this.psnLinkRepository = psnLinkRepository;
        this.gameRepository = gameRepository;
        this.gamePlatformLinkRepository = gamePlatformLinkRepository;
        this.psnIgnoredTitleRepository = psnIgnoredTitleRepository;
        this.externalPlaytimeWriter = externalPlaytimeWriter;
        this.pendingGameEnricher = pendingGameEnricher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PsnSyncResultDto importLibrary(Long userId, String accountId, PsnPlayedGamesResult library) {
        if (library.profilePrivate()) {
            return PsnSyncResultDto.privateProfile();
        }

        try {
            Counters counters = new Counters();
            counters.scanned = library.titles().size();

            LinkedPhaseResult linkedPhase = transactionTemplate.execute(
                    status -> updateLinkedTitles(userId, accountId, library.titles(), counters));

            for (PsnPlayedTitle title : linkedPhase.unlinkedTitles()) {
                try {
                    Attachment attachment = transactionTemplate.execute(status -> {
                        // Re-checked per title: if the account was unlinked/switched mid-import, stop
                        // instead of attaching the old library to the new link.
                        requireStillLinked(userId, accountId);
                        return attachOrCreate(userId, title, linkedPhase.index());
                    });
                    // Index updated only after the commit, so a rolled-back title leaves no trace in it.
                    linkedPhase.index().remember(attachment);
                    counters.record(attachment.outcome());
                } catch (PsnIntegrationException | SteamIntegrationException e) {
                    throw e;
                } catch (RuntimeException e) {
                    counters.errored++;
                    // Class name only: driver messages can echo the offending values.
                    log.warn("PSN import of title {} for userId {} failed: {}",
                            title.titleId(), userId, e.getClass().getSimpleName());
                }
            }

            return counters.toDto();
        } finally {
            // Background IGDB fill of the user's pending games not looked up yet (the ones just created,
            // and any an earlier run missed). Also after a failed import: what was created stays.
            // Returns at once and never throws.
            pendingGameEnricher.enqueue(userId);
        }
    }

    // ---- Phase 1: titles that already have a link ------------------------------------------

    private LinkedPhaseResult updateLinkedTitles(Long userId, String accountId, List<PsnPlayedTitle> titles,
                                                 Counters counters) {
        // The link may have been removed or switched to another account while we were calling PSN.
        LocalDateTime now = LocalDateTime.now();
        if (psnLinkRepository.markSynced(userId, accountId, now) == 0) {
            throw PsnIntegrationException.linkChanged();
        }

        List<GamePlatformLink> links = gamePlatformLinkRepository
                .findAllByUserIdAndPlatformInWithGame(userId, PsnPlatforms.ALL);
        Map<String, GamePlatformLink> linksByTitleId = links.stream()
                .collect(Collectors.toMap(GamePlatformLink::getExternalId, Function.identity(), (a, b) -> a));
        Set<String> ignoredTitleIds = psnIgnoredTitleRepository.findTitleIdsByUserId(userId);

        List<PsnPlayedTitle> unlinked = new ArrayList<>();
        for (PsnPlayedTitle title : titles) {
            GamePlatformLink link = linksByTitleId.get(title.titleId());
            if (link == null) {
                if (ignoredTitleIds.contains(title.titleId())) {
                    counters.ignored++;
                } else {
                    unlinked.add(title);
                }
                continue;
            }
            // Only overwritten with a known value: a missing date never erases an earlier one.
            if (title.lastPlayedAt() != null) {
                link.setLastPlayedAt(title.lastPlayedAt());
            }
            if (externalPlaytimeWriter.applySyncedTotal(link, title.playtimeMinutes(), now)) {
                counters.updated++;
            }
        }

        return new LinkedPhaseResult(unlinked, buildNameIndex(userId, links));
    }

    private void requireStillLinked(Long userId, String accountId) {
        if (!psnLinkRepository.isLinkedTo(userId, accountId)) {
            throw PsnIntegrationException.linkChanged();
        }
    }

    private NameIndex buildNameIndex(Long userId, List<GamePlatformLink> psnLinks) {
        Map<String, Long> gameIdByName = new HashMap<>();
        for (Game game : gameRepository.findAllByUserId(userId)) {
            String key = nameKey(game.getName());
            // A name that is only marks/suffix has no key: it must never attract another title.
            if (!key.isEmpty()) gameIdByName.putIfAbsent(key, game.getId());
        }
        Map<ExternalPlatform, Set<Long>> linkedGameIds = new EnumMap<>(ExternalPlatform.class);
        for (GamePlatformLink link : psnLinks) {
            linkedGameIds.computeIfAbsent(link.getPlatform(), p -> new HashSet<>()).add(link.getGame().getId());
        }
        return new NameIndex(gameIdByName, linkedGameIds);
    }

    // ---- Phase 2: one transaction per title without a link --------------------------------

    private Attachment attachOrCreate(Long userId, PsnPlayedTitle title, NameIndex index) {
        String name = truncate(title.name());
        String key = nameKey(name);
        LocalDateTime now = LocalDateTime.now();

        Long existingGameId = key.isEmpty() ? null : index.gameIdByName().get(key);
        if (existingGameId != null) {
            if (index.isLinked(title.platform(), existingGameId)) {
                // Another title of the same console already owns that game (regional versions).
                return new Attachment(Outcome.SKIPPED, null, key, title.platform());
            }
            // The user already has this game: by hand (its hours very likely include part of the PSN
            // total), from Steam, or from the other console's title. The current PSN playtime becomes
            // the baseline and only future deltas are added; a still-pending game imports every link's
            // total when it is confirmed.
            Game existing = gameRepository.getReferenceById(existingGameId);
            // Fill in a missing cover only; a user-provided cover is never overwritten.
            String cover = coverUrl(title);
            if (cover != null && isBlank(existing.getCoverImageUrl())) {
                existing.setCoverImageUrl(cover);
            }
            gamePlatformLinkRepository.save(newLink(userId, existing, title, now));
            return new Attachment(Outcome.LINKED_EXISTING, existingGameId, key, title.platform());
        }

        String cover = coverUrl(title);
        Game game = gameRepository.save(Game.builder()
                .userId(userId)
                .name(name)
                .category(null)
                .reviewStatus(ReviewStatus.PENDING_REVIEW)
                .coverImageUrl(cover)
                // Remembered so the IGDB enrichment only replaces them while the user has not edited them.
                .importedName(name)
                .importedCoverUrl(cover)
                .genres(new HashSet<>())
                .build());
        gamePlatformLinkRepository.save(newLink(userId, game, title, now));
        return new Attachment(Outcome.CREATED_PENDING, game.getId(), key, title.platform());
    }

    private static GamePlatformLink newLink(Long userId, Game game, PsnPlayedTitle title, LocalDateTime now) {
        return GamePlatformLink.builder()
                .userId(userId)
                .game(game)
                .platform(title.platform())
                .externalId(title.titleId())
                .lastSyncedPlaytimeMinutes(title.playtimeMinutes())
                .lastSyncedAt(now)
                .lastPlayedAt(title.lastPlayedAt())
                .build();
    }

    /** PSN's title artwork (public CDN, no token needed); null when missing or too long for the column. */
    static String coverUrl(PsnPlayedTitle title) {
        String url = title.imageUrl();
        return url != null && !url.isBlank() && url.length() <= MAX_COVER_URL_LENGTH ? url : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ---- Bookkeeping ---------------------------------------------------------------------

    private enum Outcome { CREATED_PENDING, LINKED_EXISTING, SKIPPED }

    private record Attachment(Outcome outcome, Long gameId, String nameKey, ExternalPlatform platform) {
    }

    private record NameIndex(Map<String, Long> gameIdByName, Map<ExternalPlatform, Set<Long>> linkedGameIds) {
        boolean isLinked(ExternalPlatform platform, Long gameId) {
            return linkedGameIds.getOrDefault(platform, Set.of()).contains(gameId);
        }

        void remember(Attachment attachment) {
            if (attachment.gameId() == null) return;
            if (!attachment.nameKey().isEmpty()) gameIdByName.putIfAbsent(attachment.nameKey(), attachment.gameId());
            linkedGameIds.computeIfAbsent(attachment.platform(), p -> new HashSet<>()).add(attachment.gameId());
        }
    }

    private record LinkedPhaseResult(List<PsnPlayedTitle> unlinkedTitles, NameIndex index) {
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

        PsnSyncResultDto toDto() {
            return new PsnSyncResultDto(scanned, newPending, linkedExisting, updated, ignored, skipped, errored, false);
        }
    }
}
