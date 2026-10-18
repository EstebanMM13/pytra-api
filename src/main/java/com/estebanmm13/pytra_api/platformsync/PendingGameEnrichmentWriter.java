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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Database side of {@link PendingGameEnricher}: short transactions before and after the IGDB calls,
 * never one held across them.
 */
@Component
@RequiredArgsConstructor
public class PendingGameEnrichmentWriter {

    private final GameRepository gameRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final GenreRepository genreRepository;

    /** What the enricher needs to search IGDB for one pending game. */
    public record Lookup(Long gameId, String name, Set<Integer> igdbPlatformIds) {
    }

    /**
     * Lookups for the games that are still pending, have something left to fill and at least one link
     * that maps to an IGDB platform. Games deleted, confirmed or fully filled meanwhile are left out.
     */
    @Transactional(readOnly = true)
    public List<Lookup> findLookups(Long userId, List<Long> gameIds) {
        List<Lookup> lookups = new ArrayList<>();
        for (Long gameId : gameIds) {
            Game game = gameRepository.findByIdAndUserId(gameId, userId).orElse(null);
            if (game == null || game.getReviewStatus() != ReviewStatus.PENDING_REVIEW
                    || !PendingGameFill.hasEmptyFields(game)) {
                continue;
            }
            List<GamePlatformLink> links = gamePlatformLinkRepository.findAllByUserIdAndGameId(userId, gameId);
            Set<Integer> platformIds = IgdbPendingMatch.igdbPlatformIds(
                    links.stream().map(GamePlatformLink::getPlatform).toList());
            if (!platformIds.isEmpty()) {
                lookups.add(new Lookup(gameId, game.getName(), platformIds));
            }
        }
        return lookups;
    }

    /**
     * Fills the empty fields of one game from its confident IGDB match. The row is locked and re-read
     * first: a game confirmed, edited or deleted since {@link #findLookups} is respected as it is now.
     *
     * @param genres every existing genre (loaded once per batch), to map IGDB's genre names onto
     * @return whether anything was written
     */
    @Transactional
    public boolean apply(Long userId, Long gameId, IgdbGameCandidateDto match, List<Genre> genres) {
        Game game = gameRepository.findForUpdateByIdAndUserId(gameId, userId).orElse(null);
        if (game == null || game.getReviewStatus() != ReviewStatus.PENDING_REVIEW) return false;
        // Name re-checked: a pending game renamed meanwhile no longer matches what was looked up.
        if (!ImportedGameNames.nameKey(game.getName()).equals(ImportedGameNames.nameKey(match.metadata().name()))) {
            return false;
        }

        List<Genre> matched = IgdbGenreMatcher.match(match.metadata().genres(), genres).stream()
                .map(genre -> genreRepository.getReferenceById(genre.getId()))
                .toList();
        return PendingGameFill.fillEmptyFields(game, match.metadata(),
                IgdbPendingMatch.suggestCategory(match.gameModeIds()), matched);
    }

    @Transactional(readOnly = true)
    public List<Genre> findAllGenres() {
        return genreRepository.findAll();
    }
}
