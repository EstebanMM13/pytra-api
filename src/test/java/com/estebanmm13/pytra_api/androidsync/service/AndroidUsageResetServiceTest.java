package com.estebanmm13.pytra_api.androidsync.service;

import com.estebanmm13.pytra_api.androidsync.dto.AndroidUsageResetResponseDto;
import com.estebanmm13.pytra_api.androidsync.model.AndroidUsageDay;
import com.estebanmm13.pytra_api.androidsync.repository.AndroidUsageDayRepository;
import com.estebanmm13.pytra_api.diary.model.DiaryEntry;
import com.estebanmm13.pytra_api.diary.model.DiaryEntryGame;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryRepository;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.service.experienceService.ExperienceService;
import com.estebanmm13.pytra_api.experiences.service.hybridPlaytime.HybridPlaytimeReconciler;
import com.estebanmm13.pytra_api.experiences.service.onlinePlaytimeService.OnlinePlaytimeService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AndroidUsageResetServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long GAME_ID = 42L;
    private static final String PACKAGE = "com.example.game";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);

    private final GamePlatformLinkRepository linkRepository = mock(GamePlatformLinkRepository.class);
    private final AndroidUsageDayRepository usageDayRepository = mock(AndroidUsageDayRepository.class);
    private final DiaryEntryRepository diaryEntryRepository = mock(DiaryEntryRepository.class);
    private final ExperienceService experienceService = mock(ExperienceService.class);
    private final OnlinePlaytimeService onlinePlaytimeService = mock(OnlinePlaytimeService.class);
    private final HybridPlaytimeReconciler hybridPlaytimeReconciler = mock(HybridPlaytimeReconciler.class);
    private final PlatformService platformService = mock(PlatformService.class);
    private final AndroidUsageResetService service = new AndroidUsageResetService(linkRepository, usageDayRepository,
            diaryEntryRepository, experienceService, onlinePlaytimeService, hybridPlaytimeReconciler, platformService);

    @BeforeEach
    void setUp() {
        when(platformService.requireByLegacyCode("MOBILE")).thenReturn(Platform.builder().id(9L).legacyCode("MOBILE").build());
        when(usageDayRepository.findAllByUserIdAndPackageName(USER_ID, PACKAGE)).thenReturn(List.of());
    }

    @Test
    void singleplayerHoursLeaveTheLinksRunAndTheLinkRestarts() {
        GamePlatformLink link = link(GameCategory.SINGLEPLAYER, 1440);
        link.setExperienceId(7L);

        AndroidUsageResetResponseDto result = service.reset(USER_ID);

        assertThat(result).isEqualTo(new AndroidUsageResetResponseDto(1, 1440));
        verify(experienceService).addHours(eq(7L), eq(new BigDecimal("-24.000000")), eq(USER_ID));
        assertThat(link.getLastSyncedPlaytimeMinutes()).isZero();
        verify(usageDayRepository).deleteAllOf(USER_ID, PACKAGE);
    }

    @Test
    void onlineHoursLeaveTheMobilePlatform() {
        link(GameCategory.ONLINE, 90);

        service.reset(USER_ID);

        verify(onlinePlaytimeService).addHours(eq(GAME_ID), eq(new BigDecimal("-1.500000")), eq(9L), eq(USER_ID));
    }

    @Test
    void hybridHoursMoveBackThroughTheSyncedShare() {
        GamePlatformLink link = link(GameCategory.HYBRID, 120);

        service.reset(USER_ID);

        verify(hybridPlaytimeReconciler).applySyncedChange(eq(link), eq(120L), any(LocalDateTime.class));
        assertThat(link.getLastSyncedPlaytimeMinutes()).isZero();
    }

    @Test
    void aDeletedRunStillRestartsTheLink() {
        GamePlatformLink link = link(GameCategory.SINGLEPLAYER, 60);
        link.setExperienceId(7L);
        doThrow(new ResourceNotFoundException("Experience not found"))
                .when(experienceService).addHours(eq(7L), any(), eq(USER_ID));

        AndroidUsageResetResponseDto result = service.reset(USER_ID);

        assertThat(result.minutesRemoved()).isZero();
        assertThat(link.getLastSyncedPlaytimeMinutes()).isZero();
        verify(usageDayRepository).deleteAllOf(USER_ID, PACKAGE);
    }

    @Test
    void anUnconfirmedGameOnlyRestarts() {
        GamePlatformLink link = link(GameCategory.ONLINE, 90);
        link.getGame().setReviewStatus(ReviewStatus.PENDING_REVIEW);

        service.reset(USER_ID);

        verify(onlinePlaytimeService, never()).addHours(anyLong(), any(), any(), any());
        assertThat(link.getLastSyncedPlaytimeMinutes()).isZero();
    }

    @Test
    void theDaysMinutesLeaveTheAutoLineAndAnEmptyDayGoes() {
        link(GameCategory.ONLINE, 90);
        when(usageDayRepository.findAllByUserIdAndPackageName(USER_ID, PACKAGE))
                .thenReturn(List.of(new AndroidUsageDay(USER_ID, PACKAGE, DAY, 90)));
        DiaryEntry entry = entry(line(GAME_ID, "1.50", true));
        when(diaryEntryRepository.findForUpdate(USER_ID, DAY)).thenReturn(Optional.of(entry));

        service.reset(USER_ID);

        assertThat(entry.getGames()).isEmpty();
        verify(diaryEntryRepository).delete(entry);
    }

    @Test
    void otherPlayOnTheLineAndTheUsersLinesStay() {
        link(GameCategory.ONLINE, 60);
        when(usageDayRepository.findAllByUserIdAndPackageName(USER_ID, PACKAGE))
                .thenReturn(List.of(new AndroidUsageDay(USER_ID, PACKAGE, DAY, 60)));
        // The auto line also holds 2 h synced from Steam; another game has a line the user wrote.
        DiaryEntryGame auto = line(GAME_ID, "3.00", true);
        DiaryEntryGame users = line(99L, "1.00", false);
        DiaryEntry entry = entry(auto, users);
        when(diaryEntryRepository.findForUpdate(USER_ID, DAY)).thenReturn(Optional.of(entry));

        service.reset(USER_ID);

        assertThat(auto.getHours()).isEqualByComparingTo("2.00");
        assertThat(entry.getGames()).containsExactly(auto, users);
        verify(diaryEntryRepository).save(entry);
        verify(diaryEntryRepository, never()).delete(any());
    }

    private GamePlatformLink link(GameCategory category, long minutes) {
        GamePlatformLink link = GamePlatformLink.builder()
                .userId(USER_ID)
                .game(Game.builder().id(GAME_ID).userId(USER_ID).name("Game").category(category)
                        .reviewStatus(ReviewStatus.CONFIRMED).build())
                .platform(ExternalPlatform.ANDROID)
                .externalId(PACKAGE)
                .lastSyncedPlaytimeMinutes(minutes)
                .lastSyncedAt(LocalDateTime.of(2026, 11, 2, 9, 0))
                .build();
        when(linkRepository.findAllByUserIdAndPlatformForUpdate(USER_ID, ExternalPlatform.ANDROID)).thenReturn(List.of(link));
        return link;
    }

    private static DiaryEntryGame line(Long gameId, String hours, boolean auto) {
        return DiaryEntryGame.builder().gameId(gameId).hours(new BigDecimal(hours)).autoSynced(auto).build();
    }

    private static DiaryEntry entry(DiaryEntryGame... lines) {
        DiaryEntry entry = DiaryEntry.builder().userId(USER_ID).entryDate(DAY).build();
        entry.setGames(new ArrayList<>(List.of(lines)));
        return entry;
    }
}
