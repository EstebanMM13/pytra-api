package com.estebanmm13.pytra_api.metadata.client;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SteamReleaseDateParserTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            // Current Spanish store format (l=spanish)
            "24 FEB 2022 | 2022-02-24",
            "9 DIC 2020 | 2020-12-09",
            "18 ABR 2011 | 2011-04-18",
            "17 SEP 2020 | 2020-09-17",
            "1 ENE 2019 | 2019-01-01",
            "5 AGO 2016 | 2016-08-05",
            // Older Spanish variants
            "24 feb. 2022 | 2022-02-24",
            "17 sept. 2020 | 2020-09-17",
            "24 de febrero de 2022 | 2022-02-24",
            "3 de mayo de 2018 | 2018-05-03",
            // English store formats
            "'24 Feb, 2022' | 2022-02-24",
            "'Feb 24, 2022' | 2022-02-24",
            "'Aug 5, 2016' | 2016-08-05",
            "'5 Dec, 2014' | 2014-12-05"
    })
    void parsesLocalizedDates(String raw, LocalDate expected) {
        assertThat(SteamReleaseDateParser.parse(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" -> null")
    @NullAndEmptySource
    @ValueSource(strings = {
            "Próximamente", "Coming soon", "Por confirmar", "To be announced",
            "2025", "Q1 2025", "1er trimestre 2025", "FEB 2025",
            "31 FEB 2022", "24 XYZ 2022", "24 FEB 22", "24 FEB 2022 extra"
    })
    void returnsNullWhenThereIsNoFullDate(String raw) {
        assertThat(SteamReleaseDateParser.parse(raw)).isNull();
    }
}
