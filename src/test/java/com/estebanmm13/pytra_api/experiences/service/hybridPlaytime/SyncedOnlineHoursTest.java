package com.estebanmm13.pytra_api.experiences.service.hybridPlaytime;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyncedOnlineHoursTest {

    @Test
    void withoutRunsTheWholeSyncedTotalIsOnline() {
        assertThat(SyncedOnlineHours.of(List.of(link(ExternalPlatform.STEAM, 60)), List.of())).isEqualTo(60.0);
    }

    @Test
    void runsOnTheLinkedPlatformAreTakenOut() {
        assertThat(SyncedOnlineHours.of(List.of(link(ExternalPlatform.STEAM, 60)), List.of(run("PC", 40.0))))
                .isEqualTo(20.0);
    }

    @Test
    void runsOnOtherPlatformsDoNotCount() {
        assertThat(SyncedOnlineHours.of(List.of(link(ExternalPlatform.STEAM, 60)),
                List.of(run("SWITCH", 40.0), run(null, 5.0))))
                .isEqualTo(60.0);
    }

    @Test
    void neverNegative() {
        assertThat(SyncedOnlineHours.of(List.of(link(ExternalPlatform.STEAM, 10)), List.of(run("PC", 40.0))))
                .isEqualTo(0.0);
    }

    @Test
    void aLongPcRunNeverEatsPlayStationHours() {
        List<GamePlatformLink> links = List.of(
                link(ExternalPlatform.STEAM, 60), link(ExternalPlatform.PSN_PS5, 30));

        assertThat(SyncedOnlineHours.of(links, List.of(run("PC", 70.0)))).isEqualTo(30.0);
    }

    @Test
    void psnVersionsShareOneBudgetCoveringBothConsoles() {
        List<GamePlatformLink> links = List.of(
                link(ExternalPlatform.PSN_PS4, 20), link(ExternalPlatform.PSN_PS5, 30));

        // A PS4 version played on a PS5 is logged as a PS5 run: it still comes out of the PSN total.
        assertThat(SyncedOnlineHours.of(links, List.of(run("PS5", 35.0), run("PS4", 5.0)))).isEqualTo(10.0);
    }

    @Test
    void platformsNoSyncWritesAreIgnored() {
        assertThat(SyncedOnlineHours.of(List.of(link(ExternalPlatform.XBOX, 60)), List.of())).isEqualTo(0.0);
    }

    private static GamePlatformLink link(ExternalPlatform platform, long hours) {
        return GamePlatformLink.builder().platform(platform).lastSyncedPlaytimeMinutes(hours * 60).build();
    }

    private static Experience run(String legacyCode, double hours) {
        Platform platform = legacyCode == null
                ? Platform.builder().name("Steam Deck").build()
                : Platform.builder().name(legacyCode).legacyCode(legacyCode).build();
        return Experience.builder().hours(hours).platform(platform).build();
    }
}
