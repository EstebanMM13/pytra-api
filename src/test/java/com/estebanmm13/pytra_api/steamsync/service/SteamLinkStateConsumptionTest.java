package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.auth.security.TokenGenerator;
import com.estebanmm13.pytra_api.error.InvalidTokenException;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.platformsync.PendingGameEnricher;
import com.estebanmm13.pytra_api.platformsync.PendingReviewQueue;
import com.estebanmm13.pytra_api.platformsync.PlatformLinkRemover;
import com.estebanmm13.pytra_api.steamsync.client.SteamWebApiClient;
import com.estebanmm13.pytra_api.steamsync.model.SteamLinkState;
import com.estebanmm13.pytra_api.steamsync.repository.SteamIgnoredAppRepository;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A Steam link state is single use: only the caller that wins the conditional consume gets the user id. */
class SteamLinkStateConsumptionTest {

    private final SteamLinkStateRepository stateRepository = mock(SteamLinkStateRepository.class);
    private final TokenGenerator tokenGenerator = mock(TokenGenerator.class);

    private SteamLinkServiceImpl service;

    @BeforeEach
    void setUp() {
        when(tokenGenerator.hashToken("raw")).thenReturn("hash");
        when(stateRepository.findByTokenHash("hash")).thenReturn(Optional.of(SteamLinkState.builder()
                .id(3L).userId(9L).tokenHash("hash").expiresAt(LocalDateTime.now().plusMinutes(5)).build()));
        service = new SteamLinkServiceImpl(stateRepository, mock(SteamLinkRepository.class),
                mock(SteamIgnoredAppRepository.class), mock(GamePlatformLinkRepository.class),
                mock(PendingReviewQueue.class), mock(PlatformLinkRemover.class), mock(SteamWebApiClient.class),
                mock(SteamSyncGuard.class), tokenGenerator, mock(PlatformTransactionManager.class),
                mock(PendingGameEnricher.class));
    }

    @Test
    void winningTheConditionalConsumeReturnsTheUser() {
        when(stateRepository.markConsumed(eq(3L), any())).thenReturn(1);

        assertThat(service.consumeLinkState("raw")).isEqualTo(9L);
    }

    @Test
    void losingTheConditionalConsumeIsAnInvalidState() {
        // A concurrent callback consumed it between our read and our write.
        when(stateRepository.markConsumed(eq(3L), any())).thenReturn(0);

        assertThatThrownBy(() -> service.consumeLinkState("raw"))
                .isInstanceOf(InvalidTokenException.class).hasMessage("Invalid Steam link state");
    }
}
