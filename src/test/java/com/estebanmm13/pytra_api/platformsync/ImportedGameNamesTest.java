package com.estebanmm13.pytra_api.platformsync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ImportedGameNamesTest {

    @Test
    void nameKeyIgnoresTrademarksApostrophesCaseAndSpaces() {
        assertThat(ImportedGameNames.nameKey("Marvel’s Spider-Man™"))
                .isEqualTo(ImportedGameNames.nameKey("marvel's spider-man"));
        assertThat(ImportedGameNames.nameKey("FINAL FANTASY® VII  REMAKE"))
                .isEqualTo(ImportedGameNames.nameKey("Final Fantasy VII Remake"));
        assertThat(ImportedGameNames.nameKey("Hades")).isNotEqualTo(ImportedGameNames.nameKey("Hades II"));
    }

    @Test
    void namesEqualIgnoringCaseShareAKey() {
        assertThat(ImportedGameNames.nameKey("ELDEN RING")).isEqualTo(ImportedGameNames.nameKey("Elden Ring"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Grand Theft Auto V (PlayStation®5)      | grand theft auto v",
            "Grand Theft Auto V (PlayStation 4)      | grand theft auto v",
            "Hogwarts Legacy (PS5)                   | hogwarts legacy",
            "Hogwarts Legacy (ps4)                   | hogwarts legacy",
            "Ratchet & Clank (PS4 & PS5)             | ratchet & clank",
            "Ratchet & Clank PS4 & PS5               | ratchet & clank",
            "Tom Clancy’s Rainbow Six® Siege         | tom clancy's rainbow six siege",
            "“Bunny” Game                            | \"bunny\" game",
            "Ｆｉｆａ　２３                           | fifa 23",
            "Final Fantasy Ⅻ                         | final fantasy xii",
    })
    void nameKeyNormalizesStoreNames(String storeName, String expectedKey) {
        assertThat(ImportedGameNames.nameKey(storeName)).isEqualTo(expectedKey);
    }

    @Test
    void trademarkMarksAreRemovedBeforeNfkcSoTheyNeverBecomeLetters() {
        assertThat(ImportedGameNames.nameKey("Spider-Man™")).isEqualTo("spider-man");
    }

    @Test
    void consoleNamesThatAreNotASuffixAreKept() {
        assertThat(ImportedGameNames.nameKey("PS4 Party Pack")).isEqualTo("ps4 party pack");
        assertThat(ImportedGameNames.nameKey("Astro's Playroom (Remastered)")).isEqualTo("astro's playroom (remastered)");
        assertThat(ImportedGameNames.nameKey("Gran Turismo 7 PS5")).isEqualTo("gran turismo 7 ps5");
    }

    @Test
    void cleanNameKeepsCase() {
        assertThat(ImportedGameNames.cleanName("  Marvel’s Spider-Man™ (PS5) ")).isEqualTo("Marvel's Spider-Man");
    }

    @Test
    void truncateKeepsTheStoreNameForPlaceholders() {
        assertThat(ImportedGameNames.truncate("  Marvel's Spider-Man™ ")).isEqualTo("Marvel's Spider-Man™");
        assertThat(ImportedGameNames.truncate(null)).isEqualTo("Unknown");
        assertThat(ImportedGameNames.truncate(" ")).isEqualTo("Unknown");
        assertThat(ImportedGameNames.truncate("x".repeat(300))).hasSize(ImportedGameNames.MAX_NAME_LENGTH);
    }
}
