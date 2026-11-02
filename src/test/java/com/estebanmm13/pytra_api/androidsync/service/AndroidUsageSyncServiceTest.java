package com.estebanmm13.pytra_api.androidsync.service;

import com.estebanmm13.pytra_api.androidsync.dto.AndroidUsageDayDto;
import com.estebanmm13.pytra_api.androidsync.dto.AndroidUsageSyncResponseDto;
import com.estebanmm13.pytra_api.androidsync.model.AndroidUsageDay;
import com.estebanmm13.pytra_api.androidsync.repository.AndroidUsageDayRepository;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.platformsync.ExternalPlaytimeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Delta of the phone's re-sent daily totals against the stored baseline (repositories faked in memory). */
class AndroidUsageSyncServiceTest {

    private static final Long USER_ID = 5L;
    private static final String GAME_PACKAGE = "com.supercell.clashroyale";
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** 2026-11-02 09:00 UTC = 10:00 in Madrid. */
    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    private final GamePlatformLinkRepository linkRepository = mock(GamePlatformLinkRepository.class);
    private final AndroidUsageDayRepository usageDayRepository = mock(AndroidUsageDayRepository.class);
    private final ExternalPlaytimeWriter playtimeWriter = mock(ExternalPlaytimeWriter.class);
    private final Map<AndroidUsageDay.Key, AndroidUsageDay> baselines = new HashMap<>();
    private final List<GamePlatformLink> links = new ArrayList<>();
    private final AndroidUsageSyncService service = new AndroidUsageSyncService(
            linkRepository, usageDayRepository, playtimeWriter, MADRID, Clock.fixed(NOW, UTC));
    private GamePlatformLink link;

    @BeforeEach
    void setUp() {
        link = link(GAME_PACKAGE, LocalDateTime.of(2026, 10, 20, 12, 0));
        links.add(link);
        when(linkRepository.findAllByUserIdAndPlatformForUpdate(USER_ID, ExternalPlatform.ANDROID))
                .thenAnswer(inv -> Collections.unmodifiableList(links));
        doAnswer(inv -> {
            AndroidUsageDay.Key key = new AndroidUsageDay.Key(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2));
            baselines.putIfAbsent(key, new AndroidUsageDay(key.userId(), key.packageName(), key.usageDate(), 0));
            return null;
        }).when(usageDayRepository).ensureExists(any(), any(), any());
        when(usageDayRepository.findForUpdate(any(), any(), any())).thenAnswer(inv -> Optional.ofNullable(
                baselines.get(new AndroidUsageDay.Key(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)))));
    }

    @Test
    void firstSyncRecordsTheWholeDayAndStoresItAsTheBaseline() {
        LocalDate day = TODAY.minusDays(1);

        AndroidUsageSyncResponseDto result = sync(new AndroidUsageDayDto(day, GAME_PACKAGE, 75));

        assertThat(result).isEqualTo(new AndroidUsageSyncResponseDto(1, 0));
        verify(playtimeWriter).applyObservedPlay(link, 75, day, LocalDateTime.of(2026, 11, 2, 9, 0));
        assertThat(baseline(day)).isEqualTo(75);
    }

    @Test
    void resendingTheSameTotalAddsNothing() {
        LocalDate day = TODAY.minusDays(1);
        sync(new AndroidUsageDayDto(day, GAME_PACKAGE, 75));

        AndroidUsageSyncResponseDto again = sync(new AndroidUsageDayDto(day, GAME_PACKAGE, 75));

        assertThat(again).isEqualTo(new AndroidUsageSyncResponseDto(0, 1));
        verify(playtimeWriter).applyObservedPlay(any(), anyLong(), any(), any());
        assertThat(baseline(day)).isEqualTo(75);
    }

    @Test
    void aGrowingTotalRecordsOnlyTheNewMinutes() {
        sync(new AndroidUsageDayDto(TODAY, GAME_PACKAGE, 30));

        AndroidUsageSyncResponseDto result = sync(new AndroidUsageDayDto(TODAY, GAME_PACKAGE, 50));

        assertThat(result.recorded()).isEqualTo(1);
        verify(playtimeWriter).applyObservedPlay(link, 30, TODAY, LocalDateTime.of(2026, 11, 2, 9, 0));
        verify(playtimeWriter).applyObservedPlay(link, 20, TODAY, LocalDateTime.of(2026, 11, 2, 9, 0));
        assertThat(baseline(TODAY)).isEqualTo(50);
    }

    @Test
    void aSmallerTotalKeepsTheBaselineAndAddsNothing() {
        sync(new AndroidUsageDayDto(TODAY, GAME_PACKAGE, 50));

        AndroidUsageSyncResponseDto result = sync(new AndroidUsageDayDto(TODAY, GAME_PACKAGE, 40));

        assertThat(result).isEqualTo(new AndroidUsageSyncResponseDto(0, 1));
        assertThat(baseline(TODAY)).isEqualTo(50);
    }

    @Test
    void anUnlinkedPackageIsIgnoredWithoutABaseline() {
        AndroidUsageSyncResponseDto result = sync(new AndroidUsageDayDto(TODAY, "com.whatsapp", 120));

        assertThat(result).isEqualTo(new AndroidUsageSyncResponseDto(0, 1));
        assertThat(baselines).isEmpty();
        verify(playtimeWriter, never()).applyObservedPlay(any(), anyLong(), any(), any());
    }

    @Test
    void datesOutOfTheWindowAndMinutesOutOfRangeAreIgnored() {
        AndroidUsageSyncResponseDto result = sync(
                new AndroidUsageDayDto(TODAY.plusDays(1), GAME_PACKAGE, 10),
                new AndroidUsageDayDto(TODAY.minusDays(15), GAME_PACKAGE, 10),
                new AndroidUsageDayDto(TODAY, GAME_PACKAGE, -1),
                new AndroidUsageDayDto(TODAY, GAME_PACKAGE, 1441),
                new AndroidUsageDayDto(null, GAME_PACKAGE, 10),
                new AndroidUsageDayDto(TODAY, GAME_PACKAGE, null),
                new AndroidUsageDayDto(TODAY.minusDays(14), GAME_PACKAGE, 1440));

        // Only the last one (14 days back, a whole day) is in range... but before the link: baseline only.
        assertThat(result).isEqualTo(new AndroidUsageSyncResponseDto(0, 7));
        verify(playtimeWriter, never()).applyObservedPlay(any(), anyLong(), any(), any());
        assertThat(baselines).hasSize(1);
        assertThat(baseline(TODAY.minusDays(14))).isEqualTo(1440);
    }

    @Test
    void theWindowEdgesAreAccepted() {
        link.setLinkedAt(null);

        AndroidUsageSyncResponseDto result = sync(
                new AndroidUsageDayDto(TODAY, GAME_PACKAGE, 1440),
                new AndroidUsageDayDto(TODAY.minusDays(14), GAME_PACKAGE, 1));

        assertThat(result).isEqualTo(new AndroidUsageSyncResponseDto(2, 0));
    }

    @Test
    void todayIsTheUsersDayNotTheServers() {
        // 23:30 UTC on Nov 2 is already Nov 3 in Madrid: Nov 3 is today, not the future.
        AndroidUsageSyncService lateService = new AndroidUsageSyncService(linkRepository, usageDayRepository,
                playtimeWriter, MADRID, Clock.fixed(Instant.parse("2026-11-02T23:30:00Z"), UTC));

        AndroidUsageSyncResponseDto result = lateService.sync(USER_ID,
                List.of(new AndroidUsageDayDto(TODAY.plusDays(1), GAME_PACKAGE, 10)));

        assertThat(result.recorded()).isEqualTo(1);
    }

    @Test
    void daysBeforeTheLinkOnlySetTheBaseline() {
        link.setLinkedAt(LocalDateTime.of(2026, 11, 1, 8, 0));

        AndroidUsageSyncResponseDto result = sync(
                new AndroidUsageDayDto(TODAY.minusDays(2), GAME_PACKAGE, 60),
                new AndroidUsageDayDto(TODAY.minusDays(1), GAME_PACKAGE, 40));

        assertThat(result).isEqualTo(new AndroidUsageSyncResponseDto(1, 1));
        verify(playtimeWriter).applyObservedPlay(link, 40, TODAY.minusDays(1), LocalDateTime.of(2026, 11, 2, 9, 0));
        assertThat(baseline(TODAY.minusDays(2))).isEqualTo(60);
    }

    @Test
    void eachLinkedPackageCountsOnItsOwn() {
        GamePlatformLink other = link("com.mojang.minecraftpe", null);
        links.add(other);

        AndroidUsageSyncResponseDto result = sync(
                new AndroidUsageDayDto(TODAY, GAME_PACKAGE, 10),
                new AndroidUsageDayDto(TODAY, "com.mojang.minecraftpe", 25));

        assertThat(result).isEqualTo(new AndroidUsageSyncResponseDto(2, 0));
        verify(playtimeWriter).applyObservedPlay(other, 25, TODAY, LocalDateTime.of(2026, 11, 2, 9, 0));
    }

    @Test
    void aMissingOrOversizedBodyIsRejected() {
        List<AndroidUsageDayDto> tooMany = Collections.nCopies(AndroidUsageSyncService.MAX_ITEMS + 1,
                new AndroidUsageDayDto(TODAY, GAME_PACKAGE, 1));

        assertThatThrownBy(() -> service.sync(USER_ID, null)).isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.INVALID_ANDROID_USAGE);
        assertThatThrownBy(() -> service.sync(USER_ID, tooMany)).isInstanceOf(InvalidRequestException.class);
    }

    private AndroidUsageSyncResponseDto sync(AndroidUsageDayDto... days) {
        return service.sync(USER_ID, List.of(days));
    }

    private int baseline(LocalDate day) {
        return baselines.get(new AndroidUsageDay.Key(USER_ID, GAME_PACKAGE, day)).getMinutesRecorded();
    }

    private static GamePlatformLink link(String packageName, LocalDateTime linkedAt) {
        return GamePlatformLink.builder()
                .userId(USER_ID)
                .game(Game.builder().id(packageName.length() + 0L).userId(USER_ID).name(packageName).build())
                .platform(ExternalPlatform.ANDROID)
                .externalId(packageName)
                .lastSyncedPlaytimeMinutes(0L)
                .lastSyncedAt(LocalDateTime.of(2026, 10, 20, 12, 0))
                .linkedAt(linkedAt)
                .build();
    }
}
