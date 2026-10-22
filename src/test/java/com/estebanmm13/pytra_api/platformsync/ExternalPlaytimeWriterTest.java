package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.experiences.service.hybridPlaytime.HybridPlaytimeReconciler;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.platforms.service.PlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalPlaytimeWriterTest {

    private static final long USER_ID = 1L;
    private static final long GAME_ID = 10L;

    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final OnlinePlaytimeRepository onlinePlaytimeRepository = mock(OnlinePlaytimeRepository.class);
    private final PlatformService platformService = mock(PlatformService.class);
    private final GamePlatformLinkRepository gamePlatformLinkRepository = mock(GamePlatformLinkRepository.class);
    private final HybridPlaytimeReconciler hybridPlaytimeReconciler =
            new HybridPlaytimeReconciler(experienceRepository, onlinePlaytimeRepository, gamePlatformLinkRepository);
    private final ExternalPlaytimeWriter writer =
            new ExternalPlaytimeWriter(experienceRepository, onlinePlaytimeRepository, platformService, hybridPlaytimeReconciler);

    @BeforeEach
    void setUp() {
        for (String code : List.of("PC", "PS4", "PS5")) {
            when(platformService.requireByLegacyCode(code))
                    .thenReturn(Platform.builder().name(code).legacyCode(code).build());
        }
        when(experienceRepository.save(any(Experience.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void singleplayerConfirmCreatesOneCanonicalRunPerLinkOnItsOwnPlatform() {
        Game game = game(GameCategory.SINGLEPLAYER, ReviewStatus.CONFIRMED);
        GamePlatformLink ps4 = link(game, ExternalPlatform.PSN_PS4, 120);
        GamePlatformLink ps5 = link(game, ExternalPlatform.PSN_PS5, 90);

        writer.importTotalsOnConfirm(List.of(ps4, ps5), game);

        ArgumentCaptor<Experience> runs = ArgumentCaptor.forClass(Experience.class);
        verify(experienceRepository, times(2)).save(runs.capture());
        assertThat(runs.getAllValues())
                .extracting(run -> run.getPlatform().getLegacyCode(), Experience::getHours, Experience::getRunLabel)
                .containsExactly(
                        tuple("PS4", 2.0, "Importado de PlayStation"),
                        tuple("PS5", 1.5, "Importado de PlayStation"));
    }

    @Test
    void steamCanonicalRunStaysOnPc() {
        Game game = game(GameCategory.SINGLEPLAYER, ReviewStatus.CONFIRMED);

        writer.importTotalsOnConfirm(List.of(link(game, ExternalPlatform.STEAM, 60)), game);

        ArgumentCaptor<Experience> run = ArgumentCaptor.forClass(Experience.class);
        verify(experienceRepository).save(run.capture());
        assertThat(run.getValue().getPlatform().getLegacyCode()).isEqualTo("PC");
        assertThat(run.getValue().getRunLabel()).isEqualTo("Importado de Steam");
    }

    @Test
    void onlineConfirmTakesMaxOfTypedHoursAndSumOfLinks() {
        Game game = game(GameCategory.ONLINE, ReviewStatus.CONFIRMED);
        OnlinePlaytime typed = OnlinePlaytime.builder().userId(USER_ID).gameId(GAME_ID).totalHours(2.0).build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(typed));

        writer.importTotalsOnConfirm(List.of(
                link(game, ExternalPlatform.PSN_PS4, 120), link(game, ExternalPlatform.PSN_PS5, 60)), game);

        assertThat(typed.getTotalHours()).isEqualTo(3.0);
        verify(onlinePlaytimeRepository).save(typed);
    }

    @Test
    void onlineConfirmKeepsLargerTypedHours() {
        Game game = game(GameCategory.HYBRID, ReviewStatus.CONFIRMED);
        OnlinePlaytime typed = OnlinePlaytime.builder().userId(USER_ID).gameId(GAME_ID).totalHours(50.0).build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(typed));

        writer.importTotalsOnConfirm(List.of(link(game, ExternalPlatform.STEAM, 600)), game);

        assertThat(typed.getTotalHours()).isEqualTo(50.0);
    }

    @Test
    void syncedTotalOnPendingGameOnlyMovesTheBaseline() {
        Game game = game(GameCategory.SINGLEPLAYER, ReviewStatus.PENDING_REVIEW);
        GamePlatformLink link = link(game, ExternalPlatform.PSN_PS5, 60);

        boolean updated = writer.applySyncedTotal(link, 120, LocalDateTime.now());

        assertThat(updated).isFalse();
        assertThat(link.getLastSyncedPlaytimeMinutes()).isEqualTo(120L);
        verify(experienceRepository, never()).save(any());
    }

    @Test
    void negativeDeltaLowersBaselineWithoutTouchingUserData() {
        Game game = game(GameCategory.SINGLEPLAYER, ReviewStatus.CONFIRMED);
        GamePlatformLink link = link(game, ExternalPlatform.PSN_PS4, 120);

        boolean updated = writer.applySyncedTotal(link, 60, LocalDateTime.now());

        assertThat(updated).isFalse();
        assertThat(link.getLastSyncedPlaytimeMinutes()).isEqualTo(60L);
        verify(experienceRepository, never()).save(any());
    }

    @Test
    void positiveDeltaOnConfirmedGameIsAddedToItsPlatformRun() {
        Game game = game(GameCategory.SINGLEPLAYER, ReviewStatus.CONFIRMED);
        GamePlatformLink link = link(game, ExternalPlatform.PSN_PS5, 60);

        boolean updated = writer.applySyncedTotal(link, 150, LocalDateTime.now());

        assertThat(updated).isTrue();
        ArgumentCaptor<Experience> run = ArgumentCaptor.forClass(Experience.class);
        verify(experienceRepository).save(run.capture());
        assertThat(run.getValue().getHours()).isEqualTo(1.5);
        assertThat(run.getValue().getPlatform().getLegacyCode()).isEqualTo("PS5");
    }

    @Test
    void hybridConfirmLeavesOutRunsAlreadyLoggedOnTheLinkedPlatform() {
        Game game = game(GameCategory.HYBRID, ReviewStatus.CONFIRMED);
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(List.of(run("PC", 40.0)));

        writer.importTotalsOnConfirm(List.of(link(game, ExternalPlatform.STEAM, 60 * 60)), game);

        ArgumentCaptor<OnlinePlaytime> online = ArgumentCaptor.forClass(OnlinePlaytime.class);
        verify(onlinePlaytimeRepository).save(online.capture());
        assertThat(online.getValue().getTotalHours()).isEqualTo(20.0);
    }

    @Test
    void onlineConfirmIgnoresRunsAndKeepsTheWholeSyncedTotal() {
        Game game = game(GameCategory.ONLINE, ReviewStatus.CONFIRMED);
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(List.of(run("PC", 40.0)));

        writer.importTotalsOnConfirm(List.of(link(game, ExternalPlatform.STEAM, 60 * 60)), game);

        ArgumentCaptor<OnlinePlaytime> online = ArgumentCaptor.forClass(OnlinePlaytime.class);
        verify(onlinePlaytimeRepository).save(online.capture());
        assertThat(online.getValue().getTotalHours()).isEqualTo(60.0);
    }

    @Test
    void hybridSyncDeltaIsAbsorbedByARunThatCoversIt() {
        Game game = game(GameCategory.HYBRID, ReviewStatus.CONFIRMED);
        GamePlatformLink link = link(game, ExternalPlatform.STEAM, 30 * 60);
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID)).thenReturn(List.of(link));
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(List.of(run("PC", 40.0)));

        boolean updated = writer.applySyncedTotal(link, 35 * 60, LocalDateTime.now());

        assertThat(updated).isFalse();
        assertThat(link.getLastSyncedPlaytimeMinutes()).isEqualTo(35L * 60);
        verify(onlinePlaytimeRepository, never()).save(any());
    }

    @Test
    void hybridSyncDeltaBeyondTheRunsGoesToOnlineHours() {
        Game game = game(GameCategory.HYBRID, ReviewStatus.CONFIRMED);
        GamePlatformLink link = link(game, ExternalPlatform.STEAM, 50 * 60);
        OnlinePlaytime online = OnlinePlaytime.builder().userId(USER_ID).gameId(GAME_ID).totalHours(15.0).build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(online));
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID)).thenReturn(List.of(link));
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(List.of(run("PC", 40.0)));

        // Share 50 - 40 = 10 -> 60 - 40 = 20: +10 on top of the 15 already there (5 typed by hand).
        boolean updated = writer.applySyncedTotal(link, 60 * 60, LocalDateTime.now());

        assertThat(updated).isTrue();
        assertThat(online.getTotalHours()).isEqualTo(25.0);
        verify(onlinePlaytimeRepository).save(online);
    }

    @Test
    void hybridNegativeDeltaStillNeverSubtracts() {
        Game game = game(GameCategory.HYBRID, ReviewStatus.CONFIRMED);
        GamePlatformLink link = link(game, ExternalPlatform.STEAM, 60 * 60);

        boolean updated = writer.applySyncedTotal(link, 30 * 60, LocalDateTime.now());

        assertThat(updated).isFalse();
        verify(onlinePlaytimeRepository, never()).save(any());
    }

    private static Experience run(String platformCode, double hours) {
        return Experience.builder()
                .userId(USER_ID)
                .gameId(GAME_ID)
                .hours(hours)
                .platform(Platform.builder().name(platformCode).legacyCode(platformCode).build())
                .build();
    }

    private static Game game(GameCategory category, ReviewStatus status) {
        return Game.builder().id(GAME_ID).userId(USER_ID).name("Game").category(category).reviewStatus(status).build();
    }

    private static GamePlatformLink link(Game game, ExternalPlatform platform, long minutes) {
        return GamePlatformLink.builder()
                .userId(USER_ID)
                .game(game)
                .platform(platform)
                .externalId(platform + "-id")
                .lastSyncedPlaytimeMinutes(minutes)
                .lastSyncedAt(LocalDateTime.now())
                .build();
    }
}
