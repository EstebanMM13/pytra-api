package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The pending-review queue is one set of games (reviewStatus = PENDING_REVIEW) shared by every
 * platform sync; each integration shows the slice that belongs to it.
 *
 * <p>A pending game belongs to a platform's queue when it has a link on one of that platform's
 * {@link ExternalPlatform values}. A game linked to Steam and PSN at the same time shows up in both.
 * {@code includeUnlinked} also keeps pending games with no link at all (Steam's queue always listed
 * them, so it keeps doing so).
 */
@Component
@RequiredArgsConstructor
public class PendingReviewQueue {

    private final GameRepository gameRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;

    public List<Game> findPending(Long userId, Set<ExternalPlatform> platforms, boolean includeUnlinked) {
        Predicate<Long> inQueue = membership(userId, platforms, includeUnlinked);
        return gameRepository.findAllByUserIdAndReviewStatus(userId, ReviewStatus.PENDING_REVIEW).stream()
                .filter(game -> inQueue.test(game.getId()))
                .toList();
    }

    public long countPending(Long userId, Set<ExternalPlatform> platforms, boolean includeUnlinked) {
        Predicate<Long> inQueue = membership(userId, platforms, includeUnlinked);
        return gameRepository.findIdsByUserIdAndReviewStatus(userId, ReviewStatus.PENDING_REVIEW).stream()
                .filter(inQueue)
                .count();
    }

    private Predicate<Long> membership(Long userId, Set<ExternalPlatform> platforms, boolean includeUnlinked) {
        Set<Long> own = gamePlatformLinkRepository.findGameIdsByUserIdAndPlatformIn(userId, platforms);
        if (!includeUnlinked) {
            return own::contains;
        }
        Set<Long> other = gamePlatformLinkRepository.findGameIdsByUserIdAndPlatformIn(userId, complementOf(platforms));
        return gameId -> own.contains(gameId) || !other.contains(gameId);
    }

    private static Set<ExternalPlatform> complementOf(Set<ExternalPlatform> platforms) {
        return EnumSet.complementOf(EnumSet.copyOf(platforms));
    }
}
