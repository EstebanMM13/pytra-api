package com.estebanmm13.pytra_api.psnsync.client;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PsnPlayDurationTest {

    @ParameterizedTest
    @CsvSource({
            "PT228H56M33S, 13737",  // 228*60 + 56 = 13736, 33 s rounds up
            "PT243H18M48S, 14599",
            "PT21M18S, 21",          // 18 s rounds down
            "PT18H, 1080",
            "PT18H20S, 1080",
            "PT4H21M, 261",
            "PT30S, 1",              // exactly half a minute rounds up
            "PT29S, 0",
            "PT0S, 0",
            "P1DT2H, 1560"           // tolerated even though PSN never sends days
    })
    void convertsIsoDurationToRoundedMinutes(String playDuration, long expectedMinutes) {
        assertThat(PsnPlayDuration.toMinutes(playDuration)).isEqualTo(expectedMinutes);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "garbage", "228:56:33", "PT-5H"})
    void missingMalformedOrNegativeIsZero(String playDuration) {
        assertThat(PsnPlayDuration.toMinutes(playDuration)).isZero();
    }
}
