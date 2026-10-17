package com.estebanmm13.pytra_api.psnsync.service;

import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.games.mapper.GameMapper;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platformsync.PendingGameConfirmer;
import com.estebanmm13.pytra_api.platformsync.PendingReviewQueue;
import com.estebanmm13.pytra_api.psnsync.client.PsnApiClient;
import com.estebanmm13.pytra_api.psnsync.dto.PsnPendingGameDto;
import com.estebanmm13.pytra_api.psnsync.model.PsnIgnoredTitle;
import com.estebanmm13.pytra_api.psnsync.repository.PsnIgnoredTitleRepository;
import com.estebanmm13.pytra_api.psnsync.repository.PsnLinkRepository;
import com.estebanmm13.pytra_api.steamsync.service.SteamSyncGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PsnSyncServiceImplTest {

    private static final long USER_ID = 1L;
    private static final long GAME_ID = 10L;

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GamePlatformLinkRepository gamePlatformLinkRepository = mock(GamePlatformLinkRepository.class);
    private final PsnIgnoredTitleRepository psnIgnoredTitleRepository = mock(PsnIgnoredTitleRepository.class);
    private final PendingReviewQueue pendingReviewQueue = mock(PendingReviewQueue.class);
    private final GameMapper gameMapper = mock(GameMapper.class);

    private PsnSyncServiceImpl service;
    private Game game;

    @BeforeEach
    void setUp() {
        service = new PsnSyncServiceImpl(mock(PsnLinkRepository.class), mock(PsnApiClient.class),
                mock(PsnLibraryImporter.class), new SteamSyncGuard(), mock(PendingGameConfirmer.class),
                pendingReviewQueue, gameRepository, gamePlatformLinkRepository, psnIgnoredTitleRepository,
                gameMapper, mock(PlatformTransactionManager.class));
        game = Game.builder().id(GAME_ID).userId(USER_ID).name("Ghost of Tsushima")
                .reviewStatus(ReviewStatus.PENDING_REVIEW).build();
        when(gameRepository.findByIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(game));
    }

    @Test
    void pendingGameWithBothConsolesShowsThePs5TitleSummedPlaytimeAndLatestPlay() {
        GamePlatformLink ps4 = link(ExternalPlatform.PSN_PS4, "CUSA13323_00", 600, Instant.parse("2021-01-01T00:00:00Z"));
        GamePlatformLink ps5 = link(ExternalPlatform.PSN_PS5, "PPSA01234_00", 120, Instant.parse("2023-05-01T00:00:00Z"));
        when(gamePlatformLinkRepository.findAllByUserIdAndPlatformInWithGame(USER_ID, PsnPlatforms.ALL))
                .thenReturn(List.of(ps4, ps5));
        when(pendingReviewQueue.findPending(USER_ID, PsnPlatforms.ALL, false)).thenReturn(List.of(game));
        GameResponseDto dto = mock(GameResponseDto.class);
        when(dto.getId()).thenReturn(GAME_ID);
        when(gameMapper.toResponseDto(game)).thenReturn(dto);

        List<PsnPendingGameDto> pending = service.getPending(USER_ID);

        assertThat(pending).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(GAME_ID);
            assertThat(item.titleId()).isEqualTo("PPSA01234_00");
            assertThat(item.psnPlatform()).isEqualTo("PS5");
            assertThat(item.psnPlaytimeMinutes()).isEqualTo(720L);
            assertThat(item.lastPlayedAt()).isEqualTo(Instant.parse("2023-05-01T00:00:00Z"));
        });
    }

    @Test
    void ignoringRecordsEveryPsnTitleAndDeletesThePsnOnlyPlaceholder() {
        GamePlatformLink ps4 = link(ExternalPlatform.PSN_PS4, "CUSA13323_00", 600, null);
        GamePlatformLink ps5 = link(ExternalPlatform.PSN_PS5, "PPSA01234_00", 120, null);
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID)).thenReturn(List.of(ps4, ps5));

        service.ignorePending(GAME_ID, USER_ID);

        ArgumentCaptor<PsnIgnoredTitle> ignored = ArgumentCaptor.forClass(PsnIgnoredTitle.class);
        verify(psnIgnoredTitleRepository, times(2)).save(ignored.capture());
        assertThat(ignored.getAllValues()).extracting(PsnIgnoredTitle::getTitleId)
                .containsExactly("CUSA13323_00", "PPSA01234_00");
        verify(gamePlatformLinkRepository).delete(ps4);
        verify(gamePlatformLinkRepository).delete(ps5);
        verify(gameRepository).delete(game);
    }

    @Test
    void ignoringKeepsAPlaceholderSteamStillLinks() {
        GamePlatformLink steam = link(ExternalPlatform.STEAM, "620", 60, null);
        GamePlatformLink ps5 = link(ExternalPlatform.PSN_PS5, "PPSA01234_00", 120, null);
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID)).thenReturn(List.of(steam, ps5));

        service.ignorePending(GAME_ID, USER_ID);

        verify(gamePlatformLinkRepository).delete(ps5);
        verify(gamePlatformLinkRepository, never()).delete(steam);
        verify(gameRepository, never()).delete(any(Game.class));
    }

    @Test
    void gameWithoutPsnLinkCannotBeIgnoredHere() {
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID))
                .thenReturn(List.of(link(ExternalPlatform.STEAM, "620", 60, null)));

        assertThatThrownBy(() -> service.ignorePending(GAME_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirmedGameCannotBeIgnored() {
        game.setReviewStatus(ReviewStatus.CONFIRMED);

        assertThatThrownBy(() -> service.ignorePending(GAME_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private GamePlatformLink link(ExternalPlatform platform, String externalId, long minutes, Instant lastPlayedAt) {
        return GamePlatformLink.builder()
                .userId(USER_ID)
                .game(game)
                .platform(platform)
                .externalId(externalId)
                .lastSyncedPlaytimeMinutes(minutes)
                .lastSyncedAt(LocalDateTime.now())
                .lastPlayedAt(lastPlayedAt)
                .build();
    }
}
