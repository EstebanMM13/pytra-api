package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Each integration only sees its own slice of the shared pending queue. */
class PendingReviewQueueTest {

    private static final long USER_ID = 1L;
    private static final Set<ExternalPlatform> STEAM = Set.of(ExternalPlatform.STEAM);
    private static final Set<ExternalPlatform> PSN = Set.of(ExternalPlatform.PSN_PS4, ExternalPlatform.PSN_PS5);

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GamePlatformLinkRepository linkRepository = mock(GamePlatformLinkRepository.class);
    private final PendingReviewQueue queue = new PendingReviewQueue(gameRepository, linkRepository);

    @BeforeEach
    void setUp() {
        // 1: Steam only, 2: PSN only, 3: Steam + PSN, 4: no link at all.
        when(gameRepository.findAllByUserIdAndReviewStatus(USER_ID, ReviewStatus.PENDING_REVIEW))
                .thenReturn(List.of(game(1), game(2), game(3), game(4)));
        when(gameRepository.findIdsByUserIdAndReviewStatus(USER_ID, ReviewStatus.PENDING_REVIEW))
                .thenReturn(List.of(1L, 2L, 3L, 4L));
        when(linkRepository.findGameIdsByUserIdAndPlatformIn(eq(USER_ID), eq(STEAM))).thenReturn(Set.of(1L, 3L));
        when(linkRepository.findGameIdsByUserIdAndPlatformIn(eq(USER_ID), eq(PSN))).thenReturn(Set.of(2L, 3L));
        when(linkRepository.findGameIdsByUserIdAndPlatformIn(eq(USER_ID), eq(EnumSet.complementOf(EnumSet.copyOf(STEAM)))))
                .thenReturn(Set.of(2L, 3L));
    }

    @Test
    void steamQueueKeepsSteamAndUnlinkedGamesButNotPsnOnlyOnes() {
        assertThat(queue.findPending(USER_ID, STEAM, true)).extracting(Game::getId).containsExactly(1L, 3L, 4L);
        assertThat(queue.countPending(USER_ID, STEAM, true)).isEqualTo(3);
    }

    @Test
    void psnQueueOnlyHasPsnLinkedGames() {
        assertThat(queue.findPending(USER_ID, PSN, false)).extracting(Game::getId).containsExactly(2L, 3L);
        assertThat(queue.countPending(USER_ID, PSN, false)).isEqualTo(2);
    }

    private static Game game(long id) {
        return Game.builder().id(id).userId(USER_ID).name("Game " + id).reviewStatus(ReviewStatus.PENDING_REVIEW).build();
    }
}
