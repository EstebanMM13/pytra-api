package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
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
    private final ExternalPlaytimeWriter writer =
            new ExternalPlaytimeWriter(experienceRepository, onlinePlaytimeRepository, platformService);

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
