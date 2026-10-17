package com.estebanmm13.pytra_api.psnsync.service;

import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.psnsync.client.PsnApiClient;
import com.estebanmm13.pytra_api.psnsync.client.PsnAuthenticator;
import com.estebanmm13.pytra_api.psnsync.model.PsnLink;
import com.estebanmm13.pytra_api.psnsync.repository.PsnLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PsnSyncSchedulerTest {

    private final PsnLinkRepository psnLinkRepository = mock(PsnLinkRepository.class);
    private final PsnSyncService psnSyncService = mock(PsnSyncService.class);
    private final PsnApiClient psnApiClient = mock(PsnApiClient.class);
    private final PsnSyncScheduler scheduler = new PsnSyncScheduler(
            psnLinkRepository, psnSyncService, psnApiClient, mock(PsnAuthenticator.class), Duration.ZERO);

    @BeforeEach
    void setUp() {
        when(psnApiClient.isConfigured()).thenReturn(true);
        when(psnLinkRepository.findAllInScheduledSyncOrder()).thenReturn(LongStream.rangeClosed(1, 5)
                .mapToObj(id -> PsnLink.builder().userId(id).accountId("acc" + id).onlineId("user" + id).build())
                .toList());
    }

    @Test
    void doesNothingWhenNotConfigured() {
        when(psnApiClient.isConfigured()).thenReturn(false);

        scheduler.syncAllLinkedAccounts();

        verify(psnSyncService, never()).sync(any());
    }

    @Test
    void syncsEveryLinkedAccount() {
        scheduler.syncAllLinkedAccounts();

        verify(psnSyncService, times(5)).sync(any());
    }

    @Test
    void abortsAtOnceWhenTheServiceAccountIsRejected() {
        when(psnSyncService.sync(any())).thenThrow(PsnIntegrationException.unavailable());
        when(psnApiClient.serviceAuthFailingSince()).thenReturn(Instant.now());

        scheduler.syncAllLinkedAccounts();

        verify(psnSyncService, times(1)).sync(any());
    }

    @Test
    void abortsAfterConsecutiveOutages() {
        when(psnSyncService.sync(any())).thenThrow(PsnIntegrationException.unavailable());

        scheduler.syncAllLinkedAccounts();

        verify(psnSyncService, times(PsnSyncScheduler.MAX_CONSECUTIVE_UNAVAILABLE)).sync(any());
    }

    @Test
    void abortsWhenRateLimited() {
        when(psnSyncService.sync(any())).thenThrow(PsnIntegrationException.rateLimited());

        scheduler.syncAllLinkedAccounts();

        verify(psnSyncService, times(1)).sync(any());
    }

    @Test
    void followsTheLeastRecentlySyncedOrder() {
        when(psnLinkRepository.findAllInScheduledSyncOrder()).thenReturn(List.of(
                PsnLink.builder().userId(4L).accountId("a4").onlineId("u4").build(),
                PsnLink.builder().userId(2L).accountId("a2").onlineId("u2").build()));

        scheduler.syncAllLinkedAccounts();

        InOrder inOrder = inOrder(psnSyncService);
        inOrder.verify(psnSyncService).sync(4L);
        inOrder.verify(psnSyncService).sync(2L);
    }

    @Test
    void stopsBeforeCallingPsnWhileRateLimited() {
        when(psnApiClient.isRateLimited()).thenReturn(true);

        scheduler.syncAllLinkedAccounts();

        verify(psnSyncService, never()).sync(any());
    }

    @Test
    void stopsOnceACallGetsRateLimited() {
        when(psnSyncService.sync(2L)).thenAnswer(inv -> {
            when(psnApiClient.isRateLimited()).thenReturn(true);
            throw PsnIntegrationException.rateLimited();
        });

        scheduler.syncAllLinkedAccounts();

        verify(psnSyncService).sync(1L);
        verify(psnSyncService).sync(2L);
        verify(psnSyncService, never()).sync(3L);
    }

    @Test
    void skipsUsersWithASyncInProgressAndContinues() {
        when(psnSyncService.sync(1L)).thenThrow(SteamIntegrationException.syncInProgress());

        scheduler.syncAllLinkedAccounts();

        verify(psnSyncService, times(5)).sync(any());
    }

    @Test
    void privateOrUnknownAccountsDoNotStopTheRun() {
        when(psnSyncService.sync(2L)).thenThrow(PsnIntegrationException.userNotFound());

        scheduler.syncAllLinkedAccounts();

        verify(psnSyncService, times(5)).sync(any());
        List.of(1L, 3L, 4L, 5L).forEach(id -> verify(psnSyncService).sync(id));
    }
}
