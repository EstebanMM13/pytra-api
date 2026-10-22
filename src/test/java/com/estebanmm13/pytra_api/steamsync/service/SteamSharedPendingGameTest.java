package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.auth.security.TokenGenerator;
import com.estebanmm13.pytra_api.games.mapper.GameMapper;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platformsync.PendingGameConfirmer;
import com.estebanmm13.pytra_api.platformsync.PendingGameEnricher;
import com.estebanmm13.pytra_api.platformsync.PendingReviewQueue;
import com.estebanmm13.pytra_api.platformsync.PlatformLinkRemover;
import com.estebanmm13.pytra_api.steamsync.client.SteamWebApiClient;
import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import com.estebanmm13.pytra_api.steamsync.repository.SteamIgnoredAppRepository;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A pending placeholder can be linked to Steam and PSN at once. Steam's ignore and unlink must only
 * remove the Steam link then: deleting the game would cascade to the PSN link.
 */
class SteamSharedPendingGameTest {

    private static final long USER_ID = 1L;
    private static final long GAME_ID = 10L;

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GamePlatformLinkRepository linkRepository = mock(GamePlatformLinkRepository.class);
    private final SteamIgnoredAppRepository steamIgnoredAppRepository = mock(SteamIgnoredAppRepository.class);
    private final SteamLinkRepository steamLinkRepository = mock(SteamLinkRepository.class);
    private final PlatformLinkRemover remover = new PlatformLinkRemover(linkRepository, gameRepository);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private Game game;
    private GamePlatformLink steamLink;

    @BeforeEach
    void setUp() {
        game = Game.builder().id(GAME_ID).userId(USER_ID).name("Elden Ring").reviewStatus(ReviewStatus.PENDING_REVIEW).build();
        steamLink = GamePlatformLink.builder().userId(USER_ID).game(game).platform(ExternalPlatform.STEAM).externalId("1245620")
                .lastSyncedPlaytimeMinutes(60L).lastSyncedAt(LocalDateTime.now()).build();
        when(gameRepository.findByIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(game));
        when(linkRepository.findByUserIdAndGameIdAndPlatform(USER_ID, GAME_ID, ExternalPlatform.STEAM))
                .thenReturn(Optional.of(steamLink));
        when(linkRepository.findAllByUserIdAndPlatformWithGame(USER_ID, ExternalPlatform.STEAM)).thenReturn(List.of(steamLink));
    }

    private void alsoLinkedToPsn(boolean linked) {
        when(linkRepository.findGameIdsByUserIdAndPlatformIn(eq(USER_ID),
                eq(EnumSet.complementOf(EnumSet.of(ExternalPlatform.STEAM)))))
                .thenReturn(linked ? Set.of(GAME_ID) : Set.of());
    }

    @Test
    void ignoreKeepsAPlaceholderPsnStillLinks() {
        alsoLinkedToPsn(true);

        syncService().ignorePending(GAME_ID, USER_ID);

        verify(steamIgnoredAppRepository).save(any());
        verify(linkRepository).delete(steamLink);
        verify(gameRepository, never()).delete(any(Game.class));
    }

    @Test
    void ignoreDeletesASteamOnlyPlaceholder() {
        alsoLinkedToPsn(false);

        syncService().ignorePending(GAME_ID, USER_ID);

        verify(linkRepository).delete(steamLink);
        verify(gameRepository).delete(game);
    }

    @Test
    void unlinkKeepsAPlaceholderPsnStillLinks() {
        alsoLinkedToPsn(true);
        SteamLink link = SteamLink.builder().userId(USER_ID).steamId("76561190000000001").linkedAt(LocalDateTime.now()).build();
        when(steamLinkRepository.findByUserId(USER_ID)).thenReturn(Optional.of(link));

        linkService().unlink(USER_ID);

        verify(linkRepository).delete(steamLink);
        verify(gameRepository, never()).delete(any(Game.class));
        verify(steamIgnoredAppRepository).deleteAllByUserId(USER_ID);
        verify(steamLinkRepository).delete(link);
    }

    private SteamSyncServiceImpl syncService() {
        return new SteamSyncServiceImpl(steamLinkRepository, mock(SteamWebApiClient.class), mock(SteamLibraryImporter.class),
                new SteamSyncGuard(), mock(PendingGameConfirmer.class), mock(PendingReviewQueue.class), remover,
                gameRepository, linkRepository, steamIgnoredAppRepository, mock(GameMapper.class), transactionManager);
    }

    private SteamLinkServiceImpl linkService() {
        return new SteamLinkServiceImpl(mock(SteamLinkStateRepository.class), steamLinkRepository, steamIgnoredAppRepository,
                linkRepository, mock(PendingReviewQueue.class), remover, mock(SteamWebApiClient.class), new SteamSyncGuard(),
                mock(TokenGenerator.class), transactionManager, mock(PendingGameEnricher.class));
    }
}
