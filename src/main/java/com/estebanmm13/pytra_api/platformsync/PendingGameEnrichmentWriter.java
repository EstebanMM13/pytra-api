package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.GenreRepository;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameCandidateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Database side of {@link PendingGameEnricher}: short transactions before and after the IGDB calls,
 * never one held across them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingGameEnrichmentWriter {

    private final GameRepository gameRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final GenreRepository genreRepository;

    /**
     * What the enricher needs to search IGDB for one pending game.
     *
     * @param lastPlayedOn latest day (UTC) any of its links reports it was played; null when none does
     */
    public record Lookup(Long gameId, String name, Set<Integer> igdbPlatformIds, LocalDate lastPlayedOn) {
        public Lookup(Long gameId, String name, Set<Integer> igdbPlatformIds) {
            this(gameId, name, igdbPlatformIds, null);
        }
    }

    /** The user's pending games IGDB was never asked about (or whose lookup failed), oldest first. */
    @Transactional(readOnly = true)
    public List<Long> findGamesWithoutLookup(Long userId) {
        return gameRepository.findIdsWithoutIgdbLookup(userId, ReviewStatus.PENDING_REVIEW);
    }

    /**
     * Lookups for the games that are still pending, not looked up yet and have at least one link that
     * maps to an IGDB platform. The others are left out (the caller marks them, so they are not re-read
     * on every sync).
     */
    @Transactional(readOnly = true)
    public List<Lookup> findLookups(Long userId, List<Long> gameIds) {
        List<Lookup> lookups = new ArrayList<>();
        for (Long gameId : gameIds) {
            Game game = gameRepository.findByIdAndUserId(gameId, userId).orElse(null);
            if (game == null || game.getReviewStatus() != ReviewStatus.PENDING_REVIEW || game.getIgdbLookupAt() != null) {
                continue;
            }
            List<GamePlatformLink> links = gamePlatformLinkRepository.findAllByUserIdAndGameId(userId, gameId);
            Set<Integer> platformIds = IgdbPendingMatch.igdbPlatformIds(
                    links.stream().map(GamePlatformLink::getPlatform).toList());
            if (!platformIds.isEmpty()) {
                LocalDate lastPlayedOn = links.stream()
                        .map(GamePlatformLink::getLastPlayedAt)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .map(at -> LocalDate.ofInstant(at, ZoneOffset.UTC))
                        .orElse(null);
                lookups.add(new Lookup(gameId, game.getName(), platformIds, lastPlayedOn));
            }
        }
        return lookups;
    }

    /** Records that IGDB was asked about these games (whatever the outcome), so no later sync asks again. */
    @Transactional
    public void markLookedUp(Long userId, Collection<Long> gameIds) {
        if (!gameIds.isEmpty()) gameRepository.markIgdbLookup(userId, gameIds, LocalDateTime.now());
    }

    /**
     * Outcome of {@link #apply}.
     *
     * @param filled   whether any field was written
     * @param renameTo    IGDB's name to give the game with {@link #renameIfUntouched}; null when the name stays
     * @param currentName the game's name when it was filled (the imported one when {@code renameTo} is set)
     */
    public record Applied(boolean filled, String renameTo, String currentName) {
        static final Applied NOTHING = new Applied(false, null, null);
    }

    /**
     * Applies a confident IGDB match to one game (see {@link PendingGameFill}) and stamps its lookup. The
     * row is locked and re-read first: a game confirmed, edited or deleted since {@link #findLookups} is
     * respected as it is now. The name is NOT changed here: a rename can hit the unique name index, and
     * that must not roll back the other fields, so it is a separate step ({@link #renameIfUntouched}).
     *
     * @param genres every existing genre (loaded once per batch), to map IGDB's genre names onto
     */
    @Transactional
    public Applied apply(Long userId, Long gameId, IgdbGameCandidateDto match, List<Genre> genres) {
        Game game = gameRepository.findForUpdateByIdAndUserId(gameId, userId).orElse(null);
        if (game == null || game.getReviewStatus() != ReviewStatus.PENDING_REVIEW) return Applied.NOTHING;
        // Re-checked: a pending game renamed meanwhile no longer matches what was looked up.
        String key = ImportedGameNames.nameKey(game.getName());
        if (key.isEmpty() || !key.equals(ImportedGameNames.nameKey(match.metadata().name()))) {
            game.setIgdbLookupAt(LocalDateTime.now());
            return Applied.NOTHING;
        }

        List<Genre> matched = IgdbGenreMatcher.match(match.metadata().genres(), genres).stream()
                .map(genre -> genreRepository.getReferenceById(genre.getId()))
                .toList();
        boolean filled = PendingGameFill.fillEmptyFields(game, match.metadata(),
                IgdbPendingMatch.suggestCategory(match.gameModeIds()), matched);
        game.setIgdbLookupAt(LocalDateTime.now());
        return new Applied(filled, PendingGameFill.importedNameToReplace(game, match.metadata()), game.getName());
    }

    /**
     * Gives a pending game IGDB's canonical name, only while it still has the imported name the user never
     * edited (checked atomically by the UPDATE itself) and no other game of the user has the new one.
     * Runs in its own transaction: a unique violation from a concurrent edit only loses the rename. A
     * confident match has the same {@link ImportedGameNames#nameKey name key} as the imported name, so
     * later syncs still find the renamed game by name.
     *
     * @return whether the game was renamed
     * @throws org.springframework.dao.DataIntegrityViolationException when the name was taken meanwhile
     */
    @Transactional
    public boolean renameIfUntouched(Long userId, Long gameId, String expectedName, String newName) {
        if (gameRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, newName, gameId)) {
            log.info("IGDB name of pending game {} of userId {} not applied: another game has it", gameId, userId);
            return false;
        }
        return gameRepository.renameImported(userId, gameId, expectedName, newName,
                ReviewStatus.PENDING_REVIEW, LocalDateTime.now()) == 1;
    }

    @Transactional(readOnly = true)
    public List<Genre> findAllGenres() {
        return genreRepository.findAll();
    }
}
