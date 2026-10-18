package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.GenreRepository;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameCandidateDto;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendingGameEnrichmentWriterTest {

    private static final long USER_ID = 1L;

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GamePlatformLinkRepository linkRepository = mock(GamePlatformLinkRepository.class);
    private final GenreRepository genreRepository = mock(GenreRepository.class);
    private final PendingGameEnrichmentWriter writer =
            new PendingGameEnrichmentWriter(gameRepository, linkRepository, genreRepository);

    private final Genre aventura = Genre.builder().id(2L).name("AVENTURA").build();
    private final IgdbGameCandidateDto spiderMan = new IgdbGameCandidateDto(
            new IgdbGameMetadataDto(1074, "Marvel's Spider-Man", "Insomniac Games", "Sony", LocalDate.of(2018, 9, 7),
                    List.of("Adventure", "Indie"), "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r77.jpg",
                    List.of("PlayStation 4")),
            Set.of(48), Set.of(1));

    @Test
    void lookupsCarryTheIgdbPlatformsOfTheGameLinks() {
        Game game = pending(5L, "Marvel’s Spider-Man™");
        when(gameRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(game));
        when(linkRepository.findAllByUserIdAndGameId(USER_ID, 5L)).thenReturn(List.of(
                link(game, ExternalPlatform.PSN_PS4), link(game, ExternalPlatform.PSN_PS5)));

        assertThat(writer.findLookups(USER_ID, List.of(5L)))
                .containsExactly(new PendingGameEnrichmentWriter.Lookup(5L, "Marvel’s Spider-Man™", Set.of(48, 167)));
    }

    @Test
    void confirmedDeletedOrUnlinkedGamesAreNotLookedUp() {
        Game confirmed = pending(5L, "A");
        confirmed.setReviewStatus(ReviewStatus.CONFIRMED);
        Game unlinked = pending(6L, "B");
        when(gameRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(confirmed));
        when(gameRepository.findByIdAndUserId(6L, USER_ID)).thenReturn(Optional.of(unlinked));
        when(gameRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.empty());
        when(linkRepository.findAllByUserIdAndGameId(USER_ID, 6L)).thenReturn(List.of());

        assertThat(writer.findLookups(USER_ID, List.of(5L, 6L, 7L))).isEmpty();
    }

    @Test
    void applyFillsEmptyFieldsAndMapsGenresToExistingOnes() {
        Game game = pending(5L, "Marvel’s Spider-Man™");
        game.setDeveloper("Typed by the user");
        when(gameRepository.findForUpdateByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(game));
        when(genreRepository.getReferenceById(2L)).thenReturn(aventura);

        boolean changed = writer.apply(USER_ID, 5L, spiderMan, List.of(aventura));

        assertThat(changed).isTrue();
        assertThat(game.getDeveloper()).isEqualTo("Typed by the user");
        assertThat(game.getPublisher()).isEqualTo("Sony");
        assertThat(game.getCategory()).isEqualTo(GameCategory.SINGLEPLAYER);
        assertThat(game.getGenres()).containsExactly(aventura);
    }

    @Test
    void applySkipsAGameConfirmedOrRenamedMeanwhile() {
        Game confirmed = pending(5L, "Marvel's Spider-Man");
        confirmed.setReviewStatus(ReviewStatus.CONFIRMED);
        Game renamed = pending(6L, "Spider-Man Remastered");
        when(gameRepository.findForUpdateByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(confirmed));
        when(gameRepository.findForUpdateByIdAndUserId(6L, USER_ID)).thenReturn(Optional.of(renamed));

        assertThat(writer.apply(USER_ID, 5L, spiderMan, List.of(aventura))).isFalse();
        assertThat(writer.apply(USER_ID, 6L, spiderMan, List.of(aventura))).isFalse();
        assertThat(writer.apply(USER_ID, 7L, spiderMan, List.of(aventura))).isFalse();
        assertThat(confirmed.getDeveloper()).isNull();
        assertThat(renamed.getDeveloper()).isNull();
    }

    @Test
    void applyReplacesAnUntouchedImportedNameAndCover() {
        String psnArt = "https://image.api.playstation.com/cdn/psn-art.png";
        Game game = imported(5L, "Marvel’s Spider-Man™", psnArt);
        when(gameRepository.findForUpdateByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(game));

        assertThat(writer.apply(USER_ID, 5L, spiderMan, List.of())).isTrue();

        assertThat(game.getName()).isEqualTo("Marvel's Spider-Man");
        assertThat(game.getCoverImageUrl()).isEqualTo(spiderMan.metadata().coverImageUrl());
        assertThat(game.getIgdbLookupAt()).isNotNull();
        // Later syncs find the renamed game by the same key as the store name.
        assertThat(ImportedGameNames.nameKey(game.getName()))
                .isEqualTo(ImportedGameNames.nameKey("Marvel’s Spider-Man™"));
    }

    @Test
    void applyKeepsANameAndCoverTheUserEdited() {
        Game game = imported(5L, "Marvel’s Spider-Man™", "https://image.api.playstation.com/cdn/psn-art.png");
        game.setName("MARVEL'S SPIDER-MAN");
        game.setCoverImageUrl("https://example.com/mine.png");
        when(gameRepository.findForUpdateByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(game));

        writer.apply(USER_ID, 5L, spiderMan, List.of());

        assertThat(game.getName()).isEqualTo("MARVEL'S SPIDER-MAN");
        assertThat(game.getCoverImageUrl()).isEqualTo("https://example.com/mine.png");
        assertThat(game.getDeveloper()).isEqualTo("Insomniac Games");
    }

    @Test
    void applySkipsTheRenameWhenAnotherGameHasTheNameButFillsTheRest() {
        Game game = imported(5L, "Marvel’s Spider-Man™", null);
        when(gameRepository.findForUpdateByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(game));
        when(gameRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Marvel's Spider-Man", 5L)).thenReturn(true);

        assertThat(writer.apply(USER_ID, 5L, spiderMan, List.of())).isTrue();

        assertThat(game.getName()).isEqualTo("Marvel’s Spider-Man™");
        assertThat(game.getDeveloper()).isEqualTo("Insomniac Games");
        assertThat(game.getCoverImageUrl()).isEqualTo(spiderMan.metadata().coverImageUrl());
    }

    @Test
    void gamesAlreadyLookedUpAreNotLookedUpAgain() {
        Game game = pending(5L, "A");
        game.setIgdbLookupAt(java.time.LocalDateTime.now());
        when(gameRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(game));

        assertThat(writer.findLookups(USER_ID, List.of(5L))).isEmpty();
    }

    @Test
    void markingNothingDoesNotTouchTheDatabase() {
        writer.markLookedUp(USER_ID, List.of());

        org.mockito.Mockito.verifyNoInteractions(gameRepository);
    }

    private static Game imported(Long id, String name, String cover) {
        Game game = pending(id, name);
        game.setImportedName(name);
        game.setCoverImageUrl(cover);
        game.setImportedCoverUrl(cover);
        return game;
    }

    private static Game pending(Long id, String name) {
        return Game.builder().id(id).userId(USER_ID).name(name).reviewStatus(ReviewStatus.PENDING_REVIEW)
                .genres(new HashSet<>()).build();
    }

    private static GamePlatformLink link(Game game, ExternalPlatform platform) {
        return GamePlatformLink.builder().userId(USER_ID).game(game).platform(platform).externalId("X").build();
    }
}
