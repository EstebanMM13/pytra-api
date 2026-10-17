package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformLinkRemoverTest {

    private static final long USER_ID = 1L;
    private static final Set<ExternalPlatform> STEAM = Set.of(ExternalPlatform.STEAM);
    private static final Set<ExternalPlatform> PSN = Set.of(ExternalPlatform.PSN_PS4, ExternalPlatform.PSN_PS5);

    private final GamePlatformLinkRepository linkRepository = mock(GamePlatformLinkRepository.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final PlatformLinkRemover remover = new PlatformLinkRemover(linkRepository, gameRepository);

    @Test
    void pendingGameWithoutOtherLinksIsDeletedWithItsLink() {
        Game game = game(10L, ReviewStatus.PENDING_REVIEW);
        GamePlatformLink steam = link(game, ExternalPlatform.STEAM);

        remover.removeLinks(USER_ID, STEAM, List.of(steam));

        verify(linkRepository).delete(steam);
        verify(gameRepository).delete(game);
    }

    @Test
    void pendingGameStillLinkedToAnotherPlatformIsKept() {
        Game game = game(10L, ReviewStatus.PENDING_REVIEW);
        GamePlatformLink steam = link(game, ExternalPlatform.STEAM);
        when(linkRepository.findGameIdsByUserIdAndPlatformIn(eq(USER_ID), eq(EnumSet.complementOf(EnumSet.copyOf(STEAM)))))
                .thenReturn(Set.of(10L));

        remover.removeLinks(USER_ID, STEAM, List.of(steam));

        verify(linkRepository).delete(steam);
        verify(gameRepository, never()).delete(any(Game.class));
    }

    @Test
    void confirmedGameIsNeverDeleted() {
        Game game = game(10L, ReviewStatus.CONFIRMED);
        GamePlatformLink steam = link(game, ExternalPlatform.STEAM);

        remover.removeLinks(USER_ID, STEAM, List.of(steam));

        verify(linkRepository).delete(steam);
        verify(gameRepository, never()).delete(any(Game.class));
    }

    @Test
    void gameWithTwoRemovedLinksIsDeletedOnce() {
        Game game = game(10L, ReviewStatus.PENDING_REVIEW);

        remover.removeLinks(USER_ID, PSN, List.of(link(game, ExternalPlatform.PSN_PS4), link(game, ExternalPlatform.PSN_PS5)));

        verify(linkRepository, times(2)).delete(any(GamePlatformLink.class));
        verify(gameRepository, times(1)).delete(game);
    }

    private static Game game(long id, ReviewStatus status) {
        return Game.builder().id(id).userId(USER_ID).name("Game").reviewStatus(status).build();
    }

    private static GamePlatformLink link(Game game, ExternalPlatform platform) {
        return GamePlatformLink.builder().userId(USER_ID).game(game).platform(platform).externalId(platform + "-1")
                .lastSyncedPlaytimeMinutes(0L).lastSyncedAt(LocalDateTime.now()).build();
    }
}
