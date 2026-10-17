package com.estebanmm13.pytra_api.psnsync.service;

import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platformsync.PendingReviewQueue;
import com.estebanmm13.pytra_api.psnsync.client.PsnAccount;
import com.estebanmm13.pytra_api.psnsync.client.PsnApiClient;
import com.estebanmm13.pytra_api.psnsync.dto.PsnStatusDto;
import com.estebanmm13.pytra_api.psnsync.model.PsnLink;
import com.estebanmm13.pytra_api.psnsync.repository.PsnIgnoredTitleRepository;
import com.estebanmm13.pytra_api.psnsync.repository.PsnLinkRepository;
import com.estebanmm13.pytra_api.steamsync.service.SteamSyncGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PsnLinkServiceImplTest {

    private static final long USER_ID = 1L;
    private static final String ACCOUNT_ID = "962157895908076652";

    private final PsnLinkRepository psnLinkRepository = mock(PsnLinkRepository.class);
    private final PsnIgnoredTitleRepository psnIgnoredTitleRepository = mock(PsnIgnoredTitleRepository.class);
    private final GamePlatformLinkRepository gamePlatformLinkRepository = mock(GamePlatformLinkRepository.class);
    private final PsnApiClient psnApiClient = mock(PsnApiClient.class);

    private PsnLinkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PsnLinkServiceImpl(psnLinkRepository, psnIgnoredTitleRepository, gamePlatformLinkRepository,
                mock(GameRepository.class), mock(PendingReviewQueue.class), psnApiClient, new SteamSyncGuard(),
                mock(PlatformTransactionManager.class));
        when(psnApiClient.isConfigured()).thenReturn(true);
        when(psnLinkRepository.saveAndFlush(any(PsnLink.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gamePlatformLinkRepository.findAllByUserIdAndPlatformInWithGame(USER_ID, PsnPlatforms.ALL)).thenReturn(List.of());
        when(gamePlatformLinkRepository.findGameIdsByUserIdAndPlatformIn(USER_ID, PsnPlatforms.OTHERS)).thenReturn(Set.of());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "has space", "way_too_long_online_id", "../x"})
    void invalidOnlineIdIsRejectedWithoutCallingPsn(String onlineId) {
        assertThatThrownBy(() -> service.link(USER_ID, onlineId))
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.INVALID_ONLINE_ID);
        verify(psnApiClient, never()).findAccount(anyString());
    }

    @Test
    void notConfiguredIsRejected() {
        when(psnApiClient.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.link(USER_ID, "EstebanK13"))
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.NOT_CONFIGURED);
    }

    @Test
    void unknownOnlineIdIsNotStored() {
        when(psnApiClient.findAccount("EstebanK13")).thenThrow(PsnIntegrationException.userNotFound());

        assertThatThrownBy(() -> service.link(USER_ID, "EstebanK13"))
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.USER_NOT_FOUND);
        verify(psnLinkRepository, never()).saveAndFlush(any());
    }

    @Test
    void newLinkStoresTheResolvedAccount() {
        when(psnLinkRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(psnApiClient.findAccount("EstebanK13")).thenReturn(new PsnAccount(ACCOUNT_ID, "EstebanK13"));

        service.link(USER_ID, " EstebanK13 ");

        ArgumentCaptor<PsnLink> saved = ArgumentCaptor.forClass(PsnLink.class);
        verify(psnLinkRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.getValue().getOnlineId()).isEqualTo("EstebanK13");
        assertThat(saved.getValue().getLinkedAt()).isNotNull();
    }

    @Test
    void relinkingTheSameRenamedAccountKeepsItsDataAndSyncDate() {
        LocalDateTime lastSync = LocalDateTime.now().minusHours(1);
        PsnLink existing = PsnLink.builder().userId(USER_ID).accountId(ACCOUNT_ID).onlineId("OldName")
                .linkedAt(LocalDateTime.now().minusDays(3)).lastSyncAt(lastSync).build();
        when(psnLinkRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(psnApiClient.findAccount("NewName")).thenReturn(new PsnAccount(ACCOUNT_ID, "NewName"));

        service.link(USER_ID, "NewName");

        assertThat(existing.getOnlineId()).isEqualTo("NewName");
        assertThat(existing.getLastSyncAt()).isEqualTo(lastSync);
        verify(psnIgnoredTitleRepository, never()).deleteAllByUserId(any());
    }

    @Test
    void switchingToAnotherAccountResetsPsnData() {
        PsnLink existing = PsnLink.builder().userId(USER_ID).accountId("111").onlineId("Someone")
                .linkedAt(LocalDateTime.now()).lastSyncAt(LocalDateTime.now()).build();
        when(psnLinkRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(psnApiClient.findAccount("EstebanK13")).thenReturn(new PsnAccount(ACCOUNT_ID, "EstebanK13"));

        service.link(USER_ID, "EstebanK13");

        assertThat(existing.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(existing.getLastSyncAt()).isNull();
        verify(psnIgnoredTitleRepository).deleteAllByUserId(USER_ID);
    }

    @Test
    void unlinkWithoutLinkIsNotLinked() {
        when(psnLinkRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlink(USER_ID))
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.NOT_LINKED);
    }

    @Test
    void statusReportsServiceAuthFailure() {
        when(psnLinkRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        Instant since = Instant.parse("2026-10-01T10:00:00Z");
        when(psnApiClient.serviceAuthFailingSince()).thenReturn(since);

        PsnStatusDto status = service.getStatus(USER_ID);

        assertThat(status.linked()).isFalse();
        assertThat(status.configured()).isTrue();
        assertThat(status.serviceAuthError()).isEqualTo(PsnIntegrationException.SERVICE_AUTH_FAILED);
        assertThat(status.serviceAuthFailingSince()).isEqualTo(since);
    }
}
