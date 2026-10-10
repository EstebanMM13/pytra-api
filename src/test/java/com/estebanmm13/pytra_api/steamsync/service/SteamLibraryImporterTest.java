package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.steamsync.client.SteamOwnedGame;
import com.estebanmm13.pytra_api.steamsync.client.SteamOwnedGamesResult;
import com.estebanmm13.pytra_api.steamsync.repository.SteamIgnoredAppRepository;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Docker-free checks of how the importer fills in Steam header covers.
 * The full flow against Postgres is covered by SteamIntegrationTests (CI).
 */
class SteamLibraryImporterTest {

    private static final long USER_ID = 1L;
    private static final String STEAM_ID = "76561190000000001";

    private final SteamLinkRepository steamLinkRepository = mock(SteamLinkRepository.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GamePlatformLinkRepository gamePlatformLinkRepository = mock(GamePlatformLinkRepository.class);
    private final SteamIgnoredAppRepository steamIgnoredAppRepository = mock(SteamIgnoredAppRepository.class);

    private SteamLibraryImporter importer;

    @BeforeEach
    void setUp() {
        importer = new SteamLibraryImporter(steamLinkRepository, gameRepository, gamePlatformLinkRepository,
                steamIgnoredAppRepository, mock(SteamPlaytimeWriter.class), mock(PlatformTransactionManager.class));
        when(steamLinkRepository.markSynced(eq(USER_ID), eq(STEAM_ID), any())).thenReturn(1);
        when(steamLinkRepository.isLinkedTo(USER_ID, STEAM_ID)).thenReturn(true);
        when(gamePlatformLinkRepository.findAllByUserIdAndPlatformWithGame(USER_ID, ExternalPlatform.STEAM))
                .thenReturn(List.of());
        when(steamIgnoredAppRepository.findAppIdsByUserId(USER_ID)).thenReturn(Set.of());
    }

    @Test
    void newPendingPlaceholderGetsSteamHeaderCover() {
        when(gameRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        importer.importLibrary(USER_ID, STEAM_ID, library(new SteamOwnedGame(620, "Portal 2", 600)));

        ArgumentCaptor<Game> saved = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(saved.capture());
        assertThat(saved.getValue().getReviewStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(saved.getValue().getCoverImageUrl())
                .isEqualTo("https://cdn.cloudflare.steamstatic.com/steam/apps/620/header.jpg");
    }

    @Test
    void linkedExistingGameWithoutCoverGetsSteamHeaderCover() {
        Game existing = existingGame(10L, "Portal 2", null);

        importer.importLibrary(USER_ID, STEAM_ID, library(new SteamOwnedGame(620, "Portal 2", 600)));

        assertThat(existing.getCoverImageUrl())
                .isEqualTo("https://cdn.cloudflare.steamstatic.com/steam/apps/620/header.jpg");
    }

    @Test
    void linkedExistingGameWithBlankCoverGetsSteamHeaderCover() {
        Game existing = existingGame(10L, "Portal 2", "  ");

        importer.importLibrary(USER_ID, STEAM_ID, library(new SteamOwnedGame(620, "Portal 2", 600)));

        assertThat(existing.getCoverImageUrl())
                .isEqualTo("https://cdn.cloudflare.steamstatic.com/steam/apps/620/header.jpg");
    }

    @Test
    void linkedExistingGameKeepsUserProvidedCover() {
        Game existing = existingGame(10L, "Portal 2", "https://example.com/mine.png");

        importer.importLibrary(USER_ID, STEAM_ID, library(new SteamOwnedGame(620, "Portal 2", 600)));

        assertThat(existing.getCoverImageUrl()).isEqualTo("https://example.com/mine.png");
    }

    private Game existingGame(Long id, String name, String cover) {
        Game game = Game.builder()
                .id(id)
                .userId(USER_ID)
                .name(name)
                .coverImageUrl(cover)
                .reviewStatus(ReviewStatus.CONFIRMED)
                .genres(new HashSet<>())
                .build();
        when(gameRepository.findAllByUserId(USER_ID)).thenReturn(List.of(game));
        when(gameRepository.getReferenceById(id)).thenReturn(game);
        return game;
    }

    private static SteamOwnedGamesResult library(SteamOwnedGame... games) {
        return new SteamOwnedGamesResult(false, List.of(games));
    }
}
