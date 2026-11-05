package com.estebanmm13.pytra_api.steamsync;

import com.estebanmm13.pytra_api.AbstractIntegrationTest;
import com.estebanmm13.pytra_api.auth.security.TokenGenerator;
import com.estebanmm13.pytra_api.error.InvalidTokenException;
import com.estebanmm13.pytra_api.steamsync.model.SteamLinkState;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkStateRepository;
import com.estebanmm13.pytra_api.steamsync.service.SteamLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The conditional consume runs against the real database: one winner per link state, then rejected. */
class SteamLinkStateConsumptionIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private SteamLinkService steamLinkService;

    @Autowired
    private SteamLinkStateRepository steamLinkStateRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Test
    void markConsumedWinsOnceThenReturnsZeroAndPersistsConsumedAt() {
        Long userId = createVerifiedUser("steamstate").getId();
        String raw = steamLinkService.createLinkState(userId);
        SteamLinkState state = steamLinkStateRepository.findByTokenHash(tokenGenerator.hashToken(raw)).orElseThrow();

        assertThat(steamLinkStateRepository.markConsumed(state.getId(), LocalDateTime.now())).isEqualTo(1);
        assertThat(steamLinkStateRepository.markConsumed(state.getId(), LocalDateTime.now())).isZero();
        assertThat(steamLinkStateRepository.findById(state.getId()).orElseThrow().getConsumedAt()).isNotNull();
    }

    @Test
    void aLinkStateIsAcceptedOnceThenRejected() {
        Long userId = createVerifiedUser("steamonce").getId();
        String raw = steamLinkService.createLinkState(userId);

        assertThat(steamLinkService.consumeLinkState(raw)).isEqualTo(userId);
        assertThatThrownBy(() -> steamLinkService.consumeLinkState(raw))
                .isInstanceOf(InvalidTokenException.class).hasMessage("Invalid Steam link state");
    }
}
