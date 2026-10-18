package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PendingGameFillTest {

    private static final String PSN_ART = "https://image.api.playstation.com/cdn/psn-art.png";
    private static final String IGDB_COVER = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r77.jpg";
    private static final IgdbGameMetadataDto METADATA = new IgdbGameMetadataDto(1074, "Marvel's Spider-Man",
            "Insomniac Games", "Sony Interactive Entertainment", LocalDate.of(2018, 9, 7),
            List.of("Adventure"), IGDB_COVER, List.of("PlayStation 4"));

    private final Genre adventure = Genre.builder().id(2L).name("AVENTURA").build();

    @Test
    void emptyPendingGameGetsEveryField() {
        Game game = pending(null);

        boolean changed = PendingGameFill.fillEmptyFields(game, METADATA, GameCategory.SINGLEPLAYER, List.of(adventure));

        assertThat(changed).isTrue();
        assertThat(game.getDeveloper()).isEqualTo("Insomniac Games");
        assertThat(game.getPublisher()).isEqualTo("Sony Interactive Entertainment");
        assertThat(game.getReleaseDate()).isEqualTo(LocalDate.of(2018, 9, 7));
        assertThat(game.getCategory()).isEqualTo(GameCategory.SINGLEPLAYER);
        assertThat(game.getCoverImageUrl()).isEqualTo(IGDB_COVER);
        assertThat(game.getGenres()).containsExactly(adventure);
    }

    @Test
    void neverOverwritesAnExistingValue() {
        Genre mine = Genre.builder().id(9L).name("ACCION").build();
        Game game = pending("https://image.api.playstation.com/cdn/psn-art.png");
        game.setDeveloper("My studio");
        game.setPublisher("My publisher");
        game.setReleaseDate(LocalDate.of(2020, 1, 1));
        game.setCategory(GameCategory.ONLINE);
        game.setGenres(new HashSet<>(Set.of(mine)));

        boolean changed = PendingGameFill.fillEmptyFields(game, METADATA, GameCategory.SINGLEPLAYER, List.of(adventure));

        assertThat(changed).isFalse();
        assertThat(game.getDeveloper()).isEqualTo("My studio");
        assertThat(game.getPublisher()).isEqualTo("My publisher");
        assertThat(game.getReleaseDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(game.getCategory()).isEqualTo(GameCategory.ONLINE);
        assertThat(game.getCoverImageUrl()).isEqualTo("https://image.api.playstation.com/cdn/psn-art.png");
        assertThat(game.getGenres()).containsExactly(mine);
    }

    @Test
    void untouchedImportedCoverIsReplacedByTheIgdbPortraitCover() {
        Game game = pending(PSN_ART);
        game.setImportedCoverUrl(PSN_ART);

        assertThat(PendingGameFill.fillEmptyFields(game, METADATA, null, List.of())).isTrue();

        assertThat(game.getCoverImageUrl()).isEqualTo(IGDB_COVER);
    }

    @Test
    void coverTheUserChangedIsKept() {
        Game game = pending("https://example.com/my-cover.png");
        game.setImportedCoverUrl(PSN_ART);

        PendingGameFill.fillEmptyFields(game, METADATA, null, List.of());

        assertThat(game.getCoverImageUrl()).isEqualTo("https://example.com/my-cover.png");
    }

    @Test
    void untouchedImportedNameIsReplacedByTheIgdbName() {
        Game game = pending(null);
        game.setName("Marvel’s Spider-Man™");
        game.setImportedName("Marvel’s Spider-Man™");

        assertThat(PendingGameFill.importedNameToReplace(game, METADATA)).isEqualTo("Marvel's Spider-Man");
    }

    @Test
    void nameTheUserEditedOrAGameCreatedByHandIsKept() {
        Game edited = pending(null);
        edited.setName("Spider-Man (my copy)");
        edited.setImportedName("Marvel’s Spider-Man™");
        Game byHand = pending(null);
        Game sameName = pending(null);
        sameName.setImportedName("Marvel's Spider-Man");

        assertThat(PendingGameFill.importedNameToReplace(edited, METADATA)).isNull();
        assertThat(PendingGameFill.importedNameToReplace(byHand, METADATA)).isNull();
        assertThat(PendingGameFill.importedNameToReplace(sameName, METADATA)).isNull();
    }

    @Test
    void coverWithoutImportRecordIsKeptWhileTheOtherEmptyFieldsAreFilled() {
        Game game = pending("https://image.api.playstation.com/cdn/psn-art.png");

        assertThat(PendingGameFill.fillEmptyFields(game, METADATA, GameCategory.HYBRID, List.of())).isTrue();

        assertThat(game.getCoverImageUrl()).isEqualTo("https://image.api.playstation.com/cdn/psn-art.png");
        assertThat(game.getDeveloper()).isEqualTo("Insomniac Games");
        assertThat(game.getCategory()).isEqualTo(GameCategory.HYBRID);
        assertThat(game.getGenres()).isEmpty();
    }

    @Test
    void blankTextCountsAsEmptyAndMissingIgdbValuesLeaveFieldsAlone() {
        Game game = pending("  ");
        game.setDeveloper(" ");
        IgdbGameMetadataDto sparse = new IgdbGameMetadataDto(1, "X", null, null, null, List.of(), null, List.of());

        assertThat(PendingGameFill.fillEmptyFields(game, sparse, null, List.of())).isFalse();
        assertThat(game.getDeveloper()).isEqualTo(" ");
        assertThat(game.getCategory()).isNull();

        assertThat(PendingGameFill.fillEmptyFields(game, METADATA, null, List.of())).isTrue();
        assertThat(game.getDeveloper()).isEqualTo("Insomniac Games");
        assertThat(game.getCoverImageUrl()).isEqualTo(IGDB_COVER);
    }

    private static Game pending(String cover) {
        return Game.builder().id(5L).userId(1L).name("Marvel's Spider-Man").coverImageUrl(cover)
                .reviewStatus(ReviewStatus.PENDING_REVIEW).genres(new HashSet<>()).build();
    }
}
