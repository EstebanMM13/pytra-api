package com.estebanmm13.pytra_api.games.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameImportedValuesTest {

    private static final String PSN_ART = "https://image.api.playstation.com/cdn/psn-art.png";

    @Test
    void editingTheNameOrCoverForgetsTheImportedValue() {
        Game game = imported();

        game.forgetEditedImportedValues("My name", "https://example.com/mine.png");

        assertThat(game.getImportedName()).isNull();
        assertThat(game.getImportedCoverUrl()).isNull();
    }

    @Test
    void savingTheSameValuesKeepsThemImported() {
        Game game = imported();

        game.forgetEditedImportedValues("Bloodborne™", PSN_ART);

        assertThat(game.getImportedName()).isEqualTo("Bloodborne™");
        assertThat(game.getImportedCoverUrl()).isEqualTo(PSN_ART);
    }

    @Test
    void clearingTheCoverCountsAsAnEdit() {
        Game game = imported();

        game.forgetEditedImportedValues("Bloodborne™", null);

        assertThat(game.getImportedName()).isEqualTo("Bloodborne™");
        assertThat(game.getImportedCoverUrl()).isNull();
    }

    private static Game imported() {
        return Game.builder().name("Bloodborne™").importedName("Bloodborne™")
                .coverImageUrl(PSN_ART).importedCoverUrl(PSN_ART).reviewStatus(ReviewStatus.PENDING_REVIEW).build();
    }
}
