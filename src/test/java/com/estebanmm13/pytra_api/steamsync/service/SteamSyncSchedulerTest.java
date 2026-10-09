package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.steamsync.client.SteamWebApiClient;
import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SteamSyncSchedulerTest {

    private final SteamLinkRepository steamLinkRepository = mock(SteamLinkRepository.class);
    private final SteamSyncService steamSyncService = mock(SteamSyncService.class);
    private final SteamWebApiClient steamWebApiClient = mock(SteamWebApiClient.class);
    private final SteamSyncScheduler scheduler =
            new SteamSyncScheduler(steamLinkRepository, steamSyncService, steamWebApiClient);

    @BeforeEach
    void setUp() {
        when(steamWebApiClient.isConfigured()).thenReturn(true);
        List<SteamLink> links = LongStream.rangeClosed(1, 6)
                .mapToObj(userId -> SteamLink.builder().userId(userId).steamId("id" + userId).build())
                .toList();
        when(steamLinkRepository.findAll()).thenReturn(links);
    }

    @Test
    void abortsRunAfterThreeConsecutiveUnavailable() {
        when(steamSyncService.sync(anyLong())).thenThrow(SteamIntegrationException.unavailable());

        scheduler.syncAllLinkedAccounts();

        verify(steamSyncService, times(SteamSyncScheduler.MAX_CONSECUTIVE_UNAVAILABLE)).sync(anyLong());
    }

    @Test
    void successResetsTheUnavailableStreak() {
        when(steamSyncService.sync(anyLong()))
                .thenThrow(SteamIntegrationException.unavailable())
                .thenThrow(SteamIntegrationException.unavailable())
                .thenReturn(null)
                .thenThrow(SteamIntegrationException.unavailable())
                .thenThrow(SteamIntegrationException.unavailable())
                .thenReturn(null);

        scheduler.syncAllLinkedAccounts();

        verify(steamSyncService, times(6)).sync(anyLong());
    }

    @Test
    void skipsEverythingWhenNotConfigured() {
        when(steamWebApiClient.isConfigured()).thenReturn(false);

        scheduler.syncAllLinkedAccounts();

        verify(steamSyncService, never()).sync(anyLong());
    }
}
