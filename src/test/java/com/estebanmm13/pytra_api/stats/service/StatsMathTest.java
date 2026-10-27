package com.estebanmm13.pytra_api.stats.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatsMathTest {

    @Test
    void averageIgnoresNullRatingsAndRoundsToTwoDecimals() {
        assertThat(StatsMath.averageRating(Arrays.asList(bd("9"), bd("8"), bd("6"), null))).isEqualTo(bd("7.67"));
        assertThat(StatsMath.averageRating(List.of(bd("7"), bd("8")))).isEqualTo(bd("7.5"));
    }

    @Test
    void averageOfDecimalRatingsRoundsHalfUp() {
        // (9.25 + 8.5) / 2 = 8.875 -> 8.88
        assertThat(StatsMath.averageRating(List.of(bd("9.25"), bd("8.50")))).isEqualTo(bd("8.88"));
        assertThat(StatsMath.average(bd("16.25"), 2)).isEqualTo(bd("8.13"));
    }

    @Test
    void averageHasNoTrailingZerosNorScientificNotation() {
        assertThat(StatsMath.averageRating(List.of(bd("9.00"), bd("9.00"))).toPlainString()).isEqualTo("9");
        assertThat(StatsMath.averageRating(List.of(bd("10.00"))).toString()).isEqualTo("10");
        assertThat(StatsMath.averageRating(List.of(bd("9.50"), bd("9.50"))).toString()).isEqualTo("9.5");
    }

    @Test
    void averageIsNullWithoutRatings() {
        assertThat(StatsMath.averageRating(List.of())).isNull();
        assertThat(StatsMath.averageRating(Arrays.asList(null, null))).isNull();
        assertThat(StatsMath.average(null, 0)).isNull();
        assertThat(StatsMath.average(BigDecimal.ZERO, 0)).isNull();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void sumToleratesMissingAggregates() {
        assertThat(StatsMath.sum(null, null)).isNull();
        assertThat(StatsMath.sum(bd("7.5"), null)).isEqualTo(bd("7.5"));
        assertThat(StatsMath.sum(null, bd("8"))).isEqualTo(bd("8"));
        assertThat(StatsMath.sum(bd("7.5"), bd("8.25"))).isEqualTo(bd("15.75"));
    }

    @Test
    void medianTakesTheMiddleOrTheMeanOfTheTwoMiddleRatings() {
        assertThat(StatsMath.medianRating(Arrays.asList(bd("9"), null, bd("6"), bd("8")))).isEqualTo(bd("8"));
        assertThat(StatsMath.medianRating(List.of(bd("9.25"), bd("6"), bd("8.5"), bd("7")))).isEqualTo(bd("7.75"));
        // (8.25 + 8.5) / 2 = 8.375, rounded half up.
        assertThat(StatsMath.medianRating(List.of(bd("8.25"), bd("8.5")))).isEqualTo(bd("8.38"));
        assertThat(StatsMath.medianRating(List.of())).isNull();
    }

    @Test
    void standardDeviationIsThePopulationOne() {
        // Mean 5, squared deviations 9 + 1 + 1 + 9 = 20, / 4 = 5, sqrt = 2.236...
        assertThat(StatsMath.standardDeviation(List.of(bd("2"), bd("4"), bd("6"), bd("8")))).isEqualTo(bd("2.24"));
        assertThat(StatsMath.standardDeviation(List.of(bd("7.5")))).isEqualTo(bd("0"));
        assertThat(StatsMath.standardDeviation(Arrays.asList(null, bd("9"), bd("9")))).isEqualTo(bd("0"));
        assertThat(StatsMath.standardDeviation(List.of())).isNull();
    }

    @Test
    void round2HidesFloatingPointNoise() {
        assertThat(StatsMath.round2(10.0 / 3)).isEqualTo(3.33);
        assertThat(StatsMath.round2(0.1 + 0.2)).isEqualTo(0.3);
    }
}
