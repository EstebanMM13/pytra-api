package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Removes one platform's links (unlink, account switch, ignore) without breaking another platform's.
 *
 * <p>A pending placeholder can be shared: Steam and PSN both link a game with the same name. Deleting
 * the game would cascade (game_platform_links ON DELETE CASCADE) and silently drop the other platform's
 * link, which its next sync would then recreate. So a pending game is only deleted when no link of
 * another platform remains; otherwise it stays, in that platform's pending queue. Confirmed games are
 * never deleted: their data belongs to the user.
 *
 * <p>Must run inside the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class PlatformLinkRemover {

    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final GameRepository gameRepository;

    /**
     * Deletes {@code links} (all of them on {@code platforms}) and every pending game they leave without
     * a link of another platform.
     */
    public void removeLinks(Long userId, Set<ExternalPlatform> platforms, Collection<GamePlatformLink> links) {
        if (links.isEmpty()) {
            return;
        }
        Set<Long> gamesLinkedElsewhere = gamePlatformLinkRepository.findGameIdsByUserIdAndPlatformIn(
                userId, EnumSet.complementOf(EnumSet.copyOf(platforms)));
        Map<Long, Game> orphanedPendingGames = new LinkedHashMap<>();
        for (GamePlatformLink link : links) {
            Game game = link.getGame();
            gamePlatformLinkRepository.delete(link);
            if (game.getReviewStatus() == ReviewStatus.PENDING_REVIEW && !gamesLinkedElsewhere.contains(game.getId())) {
                orphanedPendingGames.putIfAbsent(game.getId(), game);
            }
        }
        // Each game once, after its links: a PS4 + PS5 placeholder has two links but is one game.
        orphanedPendingGames.values().forEach(gameRepository::delete);
    }
}
