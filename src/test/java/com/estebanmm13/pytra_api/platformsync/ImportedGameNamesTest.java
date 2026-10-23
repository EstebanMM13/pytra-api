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
            "Grand Theft Auto V (PlayStation®5)      | grand theft auto 5",
            "Grand Theft Auto V (PlayStation 4)      | grand theft auto 5",
            "Hogwarts Legacy (PS5)                   | hogwarts legacy",
            "Hogwarts Legacy (ps4)                   | hogwarts legacy",
            "Ratchet & Clank (PS4 & PS5)             | ratchet & clank",
            "Ratchet & Clank PS4 & PS5               | ratchet & clank",
            "Tom Clancy’s Rainbow Six® Siege         | tom clancy's rainbow six siege",
            "“Bunny” Game                            | \"bunny\" game",
            "Ｆｉｆａ　２３                           | fifa 23",
            "Final Fantasy Ⅻ                         | final fantasy 12",
    })
    void nameKeyNormalizesStoreNames(String storeName, String expectedKey) {
        assertThat(ImportedGameNames.nameKey(storeName)).isEqualTo(expectedKey);
    }

    @Test
    void trademarkMarksAreRemovedBeforeNfkcSoTheyNeverBecomeLetters() {
        assertThat(ImportedGameNames.nameKey("Spider-Man™")).isEqualTo("spider-man");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Ghost of Tsushima™: Director’s Cut | Ghost of Tsushima: Director's Cut",
            "Uncharted™, Legacy                  | Uncharted, Legacy",
            "Sackboy™! A Big Adventure           | Sackboy! A Big Adventure",
            "Marvel’s Spider-Man™’s Pack | Marvel's Spider-Man's Pack",
            "Foo™Bar                             | Foo Bar",
            "Foo™®.                         | Foo.",
    })
    void markFollowedByPunctuationLeavesNoSpaceBehind(String storeName, String expected) {
        assertThat(ImportedGameNames.cleanName(storeName)).isEqualTo(expected);
    }

    @Test
    void placeholderNameIsTheCleanNameOrTheRawNameWhenNothingIsLeft() {
        assertThat(ImportedGameNames.placeholderName(" Grand Theft Auto V (PlayStation®5) "))
                .isEqualTo("Grand Theft Auto V");
        assertThat(ImportedGameNames.placeholderName("™")).isEqualTo("™");
        assertThat(ImportedGameNames.placeholderName(null)).isEqualTo("Unknown");
        assertThat(ImportedGameNames.placeholderName("a".repeat(300)))
                .hasSize(ImportedGameNames.MAX_NAME_LENGTH);
    }

    @Test
    void markBeforePunctuationMatchesTheNameWithoutIt() {
        assertThat(ImportedGameNames.nameKey("Ghost of Tsushima™: Director's Cut"))
                .isEqualTo(ImportedGameNames.nameKey("Ghost of Tsushima: Director's Cut"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Hades II                  | Hades 2",
            "Grand Theft Auto V        | Grand Theft Auto 5",
            "Final Fantasy VII: Remake | Final Fantasy 7: Remake",
            "FINAL FANTASY XVI         | final fantasy 16",
            "Rocky-IV                  | rocky-4",
            "Civilization vi           | Civilization 6",
            "Dragon Quest XX           | Dragon Quest 20",
            "Dragon Quest XI S         | Dragon Quest 11 S",
    })
    void romanNumeralTokensMatchTheirDigits(String roman, String arabic) {
        assertThat(ImportedGameNames.nameKey(roman)).isEqualTo(ImportedGameNames.nameKey(arabic));
    }

    @Test
    void onlyStandaloneNumeralsFromTwoToTwentyAreConverted() {
        assertThat(ImportedGameNames.nameKey("I Am Bread")).isEqualTo("i am bread");
        assertThat(ImportedGameNames.nameKey("Mega Man X")).isEqualTo("mega man x")
                .isNotEqualTo(ImportedGameNames.nameKey("Mega Man 10"));
        assertThat(ImportedGameNames.nameKey("DJ Hero MIX")).isEqualTo("dj hero mix");
        assertThat(ImportedGameNames.nameKey("DIVX Player")).isEqualTo("divx player");
        assertThat(ImportedGameNames.nameKey("Civil War")).isEqualTo("civil war");
        assertThat(ImportedGameNames.nameKey("Vivid Knight")).isEqualTo("vivid knight");
        assertThat(ImportedGameNames.nameKey("Ikaruga v2")).isEqualTo("ikaruga v2");
        assertThat(ImportedGameNames.nameKey("Final Fantasy XXI")).isEqualTo("final fantasy xxi");
        assertThat(ImportedGameNames.nameKey("Hades IIII")).isEqualTo("hades iiii");
        assertThat(ImportedGameNames.nameKey("Grand Theft Auto IV")).isEqualTo("grand theft auto 4");
    }

    @Test
    void cleanNameKeepsRomanNumerals() {
        assertThat(ImportedGameNames.cleanName("Hades II")).isEqualTo("Hades II");
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

    @Test
    void disambiguatedNamesTryThePlainNameThenTheConsoleThenNumbers() {
        assertThat(ImportedGameNames.disambiguatedNames("Until Dawn", "PS5", 3))
                .containsExactly("Until Dawn", "Until Dawn (PS5)", "Until Dawn (PS5) (2)", "Until Dawn (PS5) (3)");
    }

    @Test
    void consoleSuffixKeepsTheNameKey() {
        assertThat(ImportedGameNames.nameKey("Until Dawn (PS5)")).isEqualTo(ImportedGameNames.nameKey("Until Dawn"));
        assertThat(ImportedGameNames.nameKey("Until Dawn (PS4)")).isEqualTo("until dawn");
    }

    @Test
    void disambiguatedNamesAlwaysFitTheColumn() {
        assertThat(ImportedGameNames.disambiguatedNames("x".repeat(300), "PS4", 2))
                .allSatisfy(name -> assertThat(name).hasSizeLessThanOrEqualTo(ImportedGameNames.MAX_NAME_LENGTH))
                .element(1).asString().endsWith(" (PS4)");
    }
}
