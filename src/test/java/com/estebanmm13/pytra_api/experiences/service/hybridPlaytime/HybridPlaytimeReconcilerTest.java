package com.estebanmm13.pytra_api.experiences.service.hybridPlaytime;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridPlaytimeReconcilerTest {

    private static final long USER_ID = 1L;
    private static final long GAME_ID = 10L;

    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final OnlinePlaytimeRepository onlinePlaytimeRepository = mock(OnlinePlaytimeRepository.class);
    private final GamePlatformLinkRepository gamePlatformLinkRepository = mock(GamePlatformLinkRepository.class);
    private final HybridPlaytimeReconciler reconciler =
            new HybridPlaytimeReconciler(experienceRepository, onlinePlaytimeRepository, gamePlatformLinkRepository);

    @Test
    void loggingARunOnTheSyncedPlatformMovesItsHoursOutOfOnline() {
        Game game = game(GameCategory.HYBRID, ReviewStatus.CONFIRMED);
        GamePlatformLink steam = link(game, ExternalPlatform.STEAM, 60);
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID)).thenReturn(List.of(steam));
        OnlinePlaytime online = online(60.0);
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(run("PC", 40.0)));

        Double before = reconciler.shareBeforeRunChange(game);
        reconciler.afterRunChange(game, before);

        assertThat(before).isEqualTo(60.0);
        assertThat(online.getTotalHours()).isEqualTo(20.0);
        verify(experienceRepository).flush();
    }

    @Test
    void hoursTypedByHandOnTopOfTheSyncedShareAreKept() {
        Game game = game(GameCategory.HYBRID, ReviewStatus.CONFIRMED);
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID))
                .thenReturn(List.of(link(game, ExternalPlatform.STEAM, 60)));
        // 60 synced + 10 typed for Switch play Steam cannot see.
        OnlinePlaytime online = online(70.0);
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(run("PC", 40.0)));

        reconciler.afterRunChange(game, reconciler.shareBeforeRunChange(game));

        assertThat(online.getTotalHours()).isEqualTo(30.0);
    }

    @Test
    void deletingTheRunGivesTheHoursBackToOnline() {
        Game game = game(GameCategory.HYBRID, ReviewStatus.CONFIRMED);
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID))
                .thenReturn(List.of(link(game, ExternalPlatform.STEAM, 60)));
        OnlinePlaytime online = online(20.0);
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID))
                .thenReturn(List.of(run("PC", 40.0)))
                .thenReturn(List.of());

        reconciler.afterRunChange(game, reconciler.shareBeforeRunChange(game));

        assertThat(online.getTotalHours()).isEqualTo(60.0);
    }

    @Test
    void onlineHoursNeverGoBelowZero() {
        Game game = game(GameCategory.HYBRID, ReviewStatus.CONFIRMED);
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID))
                .thenReturn(List.of(link(game, ExternalPlatform.STEAM, 60)));
        // The user lowered the online hours by hand below the synced share.
        OnlinePlaytime online = online(5.0);
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(run("PC", 40.0)));

        reconciler.afterRunChange(game, reconciler.shareBeforeRunChange(game));

        assertThat(online.getTotalHours()).isEqualTo(0.0);
    }

    @Test
    void runOnAnUnlinkedPlatformChangesNothing() {
        Game game = game(GameCategory.HYBRID, ReviewStatus.CONFIRMED);
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID))
                .thenReturn(List.of(link(game, ExternalPlatform.STEAM, 60)));
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(run("SWITCH", 40.0)));

        reconciler.afterRunChange(game, reconciler.shareBeforeRunChange(game));

        verify(onlinePlaytimeRepository, never()).save(any());
    }

    @Test
    void deletedOnlineRecordIsNotRecreatedToSubtractFromIt() {
        Game game = game(GameCategory.HYBRID, ReviewStatus.CONFIRMED);
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID))
                .thenReturn(List.of(link(game, ExternalPlatform.STEAM, 60)));
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(run("PC", 40.0)));

        reconciler.afterRunChange(game, reconciler.shareBeforeRunChange(game));

        verify(onlinePlaytimeRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = GameCategory.class, names = {"SINGLEPLAYER", "ONLINE"})
    void otherCategoriesAreNotTouched(GameCategory category) {
        Game game = game(category, ReviewStatus.CONFIRMED);

        assertThat(reconciler.shareBeforeRunChange(game)).isNull();
        reconciler.afterRunChange(game, null);

        verify(gamePlatformLinkRepository, never()).findAllByUserIdAndGameId(any(), any());
        verify(onlinePlaytimeRepository, never()).save(any());
    }

    @Test
    void pendingOrUnlinkedHybridGamesAreNotTouched() {
        assertThat(reconciler.shareBeforeRunChange(game(GameCategory.HYBRID, ReviewStatus.PENDING_REVIEW))).isNull();
        assertThat(reconciler.shareBeforeRunChange(game(GameCategory.HYBRID, ReviewStatus.CONFIRMED))).isNull();
        assertThat(reconciler.shareBeforeRunChange(null)).isNull();
    }

    @Test
    void syncedChangeCountsEveryLinkOfTheGame() {
        Game game = game(GameCategory.HYBRID, ReviewStatus.CONFIRMED);
        GamePlatformLink steam = link(game, ExternalPlatform.STEAM, 70);
        GamePlatformLink ps5 = link(game, ExternalPlatform.PSN_PS5, 30);
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID)).thenReturn(List.of(steam, ps5));
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(List.of(run("PC", 65.0)));
        OnlinePlaytime online = online(30.0);
        LocalDateTime now = LocalDateTime.now();

        // Steam went from 60h to 70h: share 0 + 30 -> 5 + 30.
        boolean updated = reconciler.applySyncedChange(steam, 60 * 60, now);

        assertThat(updated).isTrue();
        assertThat(online.getTotalHours()).isEqualTo(35.0);
        assertThat(online.getLastSessionAt()).isEqualTo(now);
    }

    private OnlinePlaytime online(double hours) {
        OnlinePlaytime online = OnlinePlaytime.builder().userId(USER_ID).gameId(GAME_ID).totalHours(hours).build();
        when(onlinePlaytimeRepository.findByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(online));
        return online;
    }

    private static Game game(GameCategory category, ReviewStatus status) {
        return Game.builder().id(GAME_ID).userId(USER_ID).name("Game").category(category).reviewStatus(status).build();
    }

    private static GamePlatformLink link(Game game, ExternalPlatform platform, long hours) {
        return GamePlatformLink.builder()
                .userId(USER_ID)
                .game(game)
                .platform(platform)
                .externalId(platform + "-id")
                .lastSyncedPlaytimeMinutes(hours * 60)
                .lastSyncedAt(LocalDateTime.now())
                .build();
    }

    private static Experience run(String legacyCode, double hours) {
        return Experience.builder()
                .userId(USER_ID)
                .gameId(GAME_ID)
                .hours(hours)
                .platform(Platform.builder().name(legacyCode).legacyCode(legacyCode).build())
                .build();
    }
}
