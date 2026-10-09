package com.estebanmm13.pytra_api.stats.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatsMathTest {

    @Test
    void averageIgnoresNullRatingsAndRoundsToTwoDecimals() {
        assertThat(StatsMath.averageRating(Arrays.asList(9, 8, 6, null))).isEqualTo(7.67);
        assertThat(StatsMath.averageRating(List.of(7, 8))).isEqualTo(7.5);
    }

    @Test
    void averageIsNullWithoutRatings() {
        assertThat(StatsMath.averageRating(List.of())).isNull();
        assertThat(StatsMath.averageRating(Arrays.asList(null, null))).isNull();
    }

    @Test
    void roundsHalfUp() {
        assertThat(StatsMath.round2(8.125)).isEqualTo(8.13);
        assertThat(StatsMath.round2(null)).isNull();
    }
}
