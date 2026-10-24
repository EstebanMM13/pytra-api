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
import com.estebanmm13.pytra_api.platformsync.ImportedRunMerger;
import com.estebanmm13.pytra_api.platformsync.ImportedGameNames;
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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.estebanmm13.pytra_api.platformsync.ImportedGameNames.nameKey;
import static com.estebanmm13.pytra_api.platformsync.ImportedGameNames.placeholderName;

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
 *
 * <p>Same name is not always same game: PSN's concept id groups the versions of one game, so a title whose
 * known concept differs from every same-name game's known concepts (Until Dawn 2015 vs its 2024 remake)
 * becomes a pending game of its own, named "Until Dawn (PS5)" when "Until Dawn" is taken. An unknown concept
 * on either side keeps the plain name match. Pending games merged before concepts were known are split
 * the same way on the next sync (see {@link #splitMergedConcepts}).
 */
@Slf4j
@Component
public class PsnLibraryImporter {

    /** games.cover_image_url is VARCHAR(500). */
    private static final int MAX_COVER_URL_LENGTH = 500;
    /** Numbered names tried for a split PSN concept once "Name" and "Name (PS5)" are taken. */
    private static final int MAX_NAME_NUMBER = 9;

    private final PsnLinkRepository psnLinkRepository;
    private final GameRepository gameRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final PsnIgnoredTitleRepository psnIgnoredTitleRepository;
    private final ExternalPlaytimeWriter externalPlaytimeWriter;
    private final ImportedRunMerger importedRunMerger;
    private final PendingGameEnricher pendingGameEnricher;
    private final TransactionTemplate transactionTemplate;

    public PsnLibraryImporter(PsnLinkRepository psnLinkRepository,
                              GameRepository gameRepository,
                              GamePlatformLinkRepository gamePlatformLinkRepository,
                              PsnIgnoredTitleRepository psnIgnoredTitleRepository,
                              ExternalPlaytimeWriter externalPlaytimeWriter,
                              ImportedRunMerger importedRunMerger,
                              PendingGameEnricher pendingGameEnricher,
                              PlatformTransactionManager transactionManager) {
        this.psnLinkRepository = psnLinkRepository;
        this.gameRepository = gameRepository;
        this.gamePlatformLinkRepository = gamePlatformLinkRepository;
        this.psnIgnoredTitleRepository = psnIgnoredTitleRepository;
        this.externalPlaytimeWriter = externalPlaytimeWriter;
        this.importedRunMerger = importedRunMerger;
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
            // Folds imported runs created before in-progress runs were adopted into the user's own run.
            importedRunMerger.mergeAll(userId);

            NameIndex index = linkedPhase.index();
            if (splitMergedConcepts(userId, accountId, linkedPhase.links(), library.titles(), counters) > 0) {
                // Links moved to new games: the index built in phase 1 no longer matches the database.
                index = transactionTemplate.execute(status -> buildNameIndex(userId,
                        gamePlatformLinkRepository.findAllByUserIdAndPlatformInWithGame(userId, PsnPlatforms.ALL)));
            }
            NameIndex nameIndex = index;

            for (PsnPlayedTitle title : linkedPhase.unlinkedTitles()) {
                try {
                    Attachment attachment = transactionTemplate.execute(status -> {
                        // Re-checked per title: if the account was unlinked/switched mid-import, stop
                        // instead of attaching the old library to the new link.
                        requireStillLinked(userId, accountId);
                        return attachOrCreate(userId, title, nameIndex);
                    });
                    // Index updated only after the commit, so a rolled-back title leaves no trace in it.
                    nameIndex.remember(attachment);
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
            // Backfills links created before PSN concepts were stored (V29); also never erased by a miss.
            if (title.conceptId() != null && !title.conceptId().equals(link.getPsnConceptId())) {
                link.setPsnConceptId(title.conceptId());
            }
            if (externalPlaytimeWriter.applySyncedTotal(link, title.playtimeMinutes(), now)) {
                counters.updated++;
            }
        }

        return new LinkedPhaseResult(unlinked, links, buildNameIndex(userId, links));
    }

    private void requireStillLinked(Long userId, String accountId) {
        if (!psnLinkRepository.isLinkedTo(userId, accountId)) {
            throw PsnIntegrationException.linkChanged();
        }
    }

    private NameIndex buildNameIndex(Long userId, List<GamePlatformLink> psnLinks) {
        // Several games can share a key (a split PSN concept is "Until Dawn (PS5)" next to "Until Dawn").
        Map<String, List<Long>> gameIdsByName = new HashMap<>();
        for (Game game : gameRepository.findAllByUserId(userId)) {
            String key = nameKey(game.getName());
            // A name that is only marks/suffix has no key: it must never attract another title.
            if (!key.isEmpty()) gameIdsByName.computeIfAbsent(key, k -> new ArrayList<>()).add(game.getId());
        }
        Map<ExternalPlatform, Set<Long>> linkedGameIds = new EnumMap<>(ExternalPlatform.class);
        Map<Long, Set<Long>> conceptsByGameId = new HashMap<>();
        for (GamePlatformLink link : psnLinks) {
            Long gameId = link.getGame().getId();
            linkedGameIds.computeIfAbsent(link.getPlatform(), p -> new HashSet<>()).add(gameId);
            if (link.getPsnConceptId() != null) {
                conceptsByGameId.computeIfAbsent(gameId, id -> new HashSet<>()).add(link.getPsnConceptId());
            }
        }
        return new NameIndex(gameIdsByName, linkedGameIds, conceptsByGameId);
    }

    // ---- Self-heal: pending games that merged two PSN concepts ------------------------------

    /**
     * Before concepts were stored, same-name titles of different games were merged into one pending game
     * (Until Dawn 2015 + its 2024 remake), which IGDB could then never match. Now that phase 1 knows their
     * concepts, each extra concept's links move to a pending game of its own; both are queued for IGDB
     * again. Confirmed games are left alone: the user already reviewed them. One transaction per game; a
     * failing game is logged and left as it is.
     *
     * @return how many pending games were created
     */
    private int splitMergedConcepts(Long userId, String accountId, List<GamePlatformLink> psnLinks,
                                    List<PsnPlayedTitle> titles, Counters counters) {
        Map<Long, Set<Long>> conceptsByPendingGame = new HashMap<>();
        for (GamePlatformLink link : psnLinks) {
            if (link.getPsnConceptId() != null && link.getGame().getReviewStatus() == ReviewStatus.PENDING_REVIEW) {
                conceptsByPendingGame.computeIfAbsent(link.getGame().getId(), id -> new HashSet<>())
                        .add(link.getPsnConceptId());
            }
        }
        List<Long> mergedGameIds = conceptsByPendingGame.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (mergedGameIds.isEmpty()) return 0;

        Map<String, PsnPlayedTitle> titlesById = titles.stream()
                .collect(Collectors.toMap(PsnPlayedTitle::titleId, Function.identity(), (a, b) -> a));
        int created = 0;
        for (Long gameId : mergedGameIds) {
            try {
                Integer split = transactionTemplate.execute(status -> {
                    requireStillLinked(userId, accountId);
                    return splitConcepts(userId, gameId, titlesById);
                });
                created += split == null ? 0 : split;
            } catch (PsnIntegrationException | SteamIntegrationException e) {
                throw e;
            } catch (RuntimeException e) {
                log.warn("PSN concept split of pending game {} for userId {} failed: {}",
                        gameId, userId, e.getClass().getSimpleName());
            }
        }
        counters.newPending += created;
        if (created > 0) {
            log.info("PSN sync for userId {} split {} merged PSN concepts into pending games of their own",
                    userId, created);
        }
        return created;
    }

    /**
     * Keeps the concept of the game's oldest PSN link (the title that created it) and moves every other
     * known concept's links to a new pending game. Links without a known concept and non-PSN links stay.
     *
     * @return pending games created
     */
    private int splitConcepts(Long userId, Long gameId, Map<String, PsnPlayedTitle> titlesById) {
        Game game = gameRepository.findForUpdateByIdAndUserId(gameId, userId).orElse(null);
        if (game == null || game.getReviewStatus() != ReviewStatus.PENDING_REVIEW) return 0;

        Map<Long, List<GamePlatformLink>> linksByConcept = new LinkedHashMap<>();
        gamePlatformLinkRepository.findAllByUserIdAndGameId(userId, gameId).stream()
                .filter(link -> PsnPlatforms.ALL.contains(link.getPlatform()) && link.getPsnConceptId() != null)
                .sorted(Comparator.comparing(GamePlatformLink::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(link -> linksByConcept.computeIfAbsent(link.getPsnConceptId(), c -> new ArrayList<>()).add(link));
        // A pending game has no runs yet; if one somehow does, moving its links would orphan them.
        boolean anyRun = linksByConcept.values().stream().flatMap(List::stream)
                .anyMatch(link -> link.getExperienceId() != null);
        if (linksByConcept.size() < 2 || anyRun) return 0;

        int created = 0;
        for (List<GamePlatformLink> links : linksByConcept.values().stream().skip(1).toList()) {
            GamePlatformLink representative = links.stream()
                    .min(Comparator.comparing(GamePlatformLink::getPlatform, PsnPlatforms.PS5_FIRST))
                    .orElseThrow();
            PsnPlayedTitle title = titlesById.get(representative.getExternalId());
            String importedName = title != null ? placeholderName(title.name())
                    : placeholderName(game.getImportedName() != null ? game.getImportedName() : game.getName());
            String cover = title != null ? coverUrl(title) : null;
            Game split = gameRepository.save(Game.builder()
                    .userId(userId)
                    .name(availableName(userId, importedName, representative.getPlatform()))
                    .category(null)
                    .reviewStatus(ReviewStatus.PENDING_REVIEW)
                    .coverImageUrl(cover)
                    .importedName(importedName)
                    .importedCoverUrl(cover)
                    .genres(new HashSet<>())
                    .build());
            links.forEach(link -> link.setGame(split));
            created++;
        }
        // Looked up again with one concept (one console) each, so IGDB can tell them apart.
        game.setIgdbLookupAt(null);
        return created;
    }

    // ---- Phase 2: one transaction per title without a link --------------------------------

    private Attachment attachOrCreate(Long userId, PsnPlayedTitle title, NameIndex index) {
        // Stored without store noise (marks, console suffix); keyed on the stored value so a miss never
        // collides with the unique index on (user_id, LOWER(name)).
        String name = placeholderName(title.name());
        String key = nameKey(name);
        LocalDateTime now = LocalDateTime.now();

        NameMatch match = key.isEmpty() ? NameMatch.NONE : index.match(key, title.conceptId());
        Long existingGameId = match.gameId();
        if (existingGameId != null) {
            if (index.isLinked(title.platform(), existingGameId)) {
                // Another title of the same console already owns that game (regional versions).
                return new Attachment(Outcome.SKIPPED, null, key, title.platform(), null);
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
            return new Attachment(Outcome.LINKED_EXISTING, existingGameId, key, title.platform(), title.conceptId());
        }

        // Every same-name game is another PSN concept (e.g. Until Dawn 2015 vs its 2024 remake): a game of
        // its own, under a name the unique index accepts. The imported name stays the plain one.
        String storedName = match.otherConcept() ? availableName(userId, name, title.platform()) : name;
        String cover = coverUrl(title);
        Game game = gameRepository.save(Game.builder()
                .userId(userId)
                .name(storedName)
                .category(null)
                .reviewStatus(ReviewStatus.PENDING_REVIEW)
                .coverImageUrl(cover)
                // Remembered so the IGDB enrichment only replaces them while the user has not edited them.
                .importedName(name)
                .importedCoverUrl(cover)
                .genres(new HashSet<>())
                .build());
        gamePlatformLinkRepository.save(newLink(userId, game, title, now));
        return new Attachment(Outcome.CREATED_PENDING, game.getId(), key, title.platform(), title.conceptId());
    }

    /**
     * First name of {@link ImportedGameNames#disambiguatedNames} no game of the user has yet ("Until Dawn
     * (PS5)" when "Until Dawn" is taken).
     *
     * @throws IllegalStateException when every candidate is taken (the title is counted as errored)
     */
    private String availableName(Long userId, String name, ExternalPlatform platform) {
        for (String candidate : ImportedGameNames.disambiguatedNames(name, PsnPlatforms.consoleCode(platform),
                MAX_NAME_NUMBER)) {
            if (!gameRepository.existsByUserIdAndNameIgnoreCase(userId, candidate)) return candidate;
        }
        throw new IllegalStateException("No free name for a split PSN concept");
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
                .psnConceptId(title.conceptId())
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

    private record Attachment(Outcome outcome, Long gameId, String nameKey, ExternalPlatform platform,
                              Long conceptId) {
    }

    /**
     * Result of looking a title up by name.
     *
     * @param gameId       the game to link the title to; null when there is none
     * @param otherConcept true when same-name games exist but all belong to other PSN concepts
     */
    private record NameMatch(Long gameId, boolean otherConcept) {
        static final NameMatch NONE = new NameMatch(null, false);
    }

    /**
     * The user's games by {@link ImportedGameNames#nameKey name key}, which consoles link each one, and the
     * known PSN concepts of each (empty for manual/Steam games and for PSN links that never reported one).
     */
    private record NameIndex(Map<String, List<Long>> gameIdsByName,
                             Map<ExternalPlatform, Set<Long>> linkedGameIds,
                             Map<Long, Set<Long>> conceptsByGameId) {
        boolean isLinked(ExternalPlatform platform, Long gameId) {
            return linkedGameIds.getOrDefault(platform, Set.of()).contains(gameId);
        }

        /**
         * The same-name game a title belongs to. A title without a known concept takes the first one (the
         * behaviour before concepts were stored). One with a concept takes the game of that concept, else one
         * with no known concept; when every same-name game has another known concept it is a different game.
         */
        NameMatch match(String key, Long conceptId) {
            List<Long> gameIds = gameIdsByName.getOrDefault(key, List.of());
            if (gameIds.isEmpty()) return NameMatch.NONE;
            if (conceptId == null) return new NameMatch(gameIds.getFirst(), false);
            for (Long gameId : gameIds) {
                if (concepts(gameId).contains(conceptId)) return new NameMatch(gameId, false);
            }
            for (Long gameId : gameIds) {
                if (concepts(gameId).isEmpty()) return new NameMatch(gameId, false);
            }
            return new NameMatch(null, true);
        }

        private Set<Long> concepts(Long gameId) {
            return conceptsByGameId.getOrDefault(gameId, Set.of());
        }

        void remember(Attachment attachment) {
            Long gameId = attachment.gameId();
            if (gameId == null) return;
            if (!attachment.nameKey().isEmpty()) {
                List<Long> sameName = gameIdsByName.computeIfAbsent(attachment.nameKey(), k -> new ArrayList<>());
                if (!sameName.contains(gameId)) sameName.add(gameId);
            }
            linkedGameIds.computeIfAbsent(attachment.platform(), p -> new HashSet<>()).add(gameId);
            if (attachment.conceptId() != null) {
                conceptsByGameId.computeIfAbsent(gameId, id -> new HashSet<>()).add(attachment.conceptId());
            }
        }
    }

    private record LinkedPhaseResult(List<PsnPlayedTitle> unlinkedTitles, List<GamePlatformLink> links,
                                     NameIndex index) {
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
