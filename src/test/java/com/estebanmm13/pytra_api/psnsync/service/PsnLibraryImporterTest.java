package com.estebanmm13.pytra_api.psnsync.service;

import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platformsync.ExternalPlaytimeWriter;
import com.estebanmm13.pytra_api.platformsync.PendingGameEnricher;
import com.estebanmm13.pytra_api.psnsync.client.PsnPlayedGamesResult;
import com.estebanmm13.pytra_api.psnsync.client.PsnPlayedTitle;
import com.estebanmm13.pytra_api.psnsync.dto.PsnSyncResultDto;
import com.estebanmm13.pytra_api.psnsync.repository.PsnIgnoredTitleRepository;
import com.estebanmm13.pytra_api.psnsync.repository.PsnLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Docker-free checks of how PSN titles become pending games and platform links.
 * The full flow against Postgres is covered by PsnIntegrationTests (CI).
 */
class PsnLibraryImporterTest {

    private static final long USER_ID = 1L;
    private static final String ACCOUNT_ID = "962157895908076652";
    private static final Instant LAST_PLAYED = Instant.parse("2024-08-03T19:28:27Z");

    private final PsnLinkRepository psnLinkRepository = mock(PsnLinkRepository.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GamePlatformLinkRepository gamePlatformLinkRepository = mock(GamePlatformLinkRepository.class);
    private final PsnIgnoredTitleRepository psnIgnoredTitleRepository = mock(PsnIgnoredTitleRepository.class);
    private final ExternalPlaytimeWriter externalPlaytimeWriter = mock(ExternalPlaytimeWriter.class);
    private final PendingGameEnricher pendingGameEnricher = mock(PendingGameEnricher.class);
    private final List<Game> userGames = new ArrayList<>();
    private final List<GamePlatformLink> savedLinks = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);
    private final Map<Long, Game> createdGames = new HashMap<>();

    private PsnLibraryImporter importer;

    @BeforeEach
    void setUp() {
        importer = new PsnLibraryImporter(psnLinkRepository, gameRepository, gamePlatformLinkRepository,
                psnIgnoredTitleRepository, externalPlaytimeWriter, pendingGameEnricher,
                mock(PlatformTransactionManager.class));
        when(psnLinkRepository.markSynced(eq(USER_ID), eq(ACCOUNT_ID), any())).thenReturn(1);
        when(psnLinkRepository.isLinkedTo(USER_ID, ACCOUNT_ID)).thenReturn(true);
        when(gamePlatformLinkRepository.findAllByUserIdAndPlatformInWithGame(USER_ID, PsnPlatforms.ALL))
                .thenReturn(List.of());
        when(psnIgnoredTitleRepository.findTitleIdsByUserId(USER_ID)).thenReturn(Set.of());
        when(gameRepository.findAllByUserId(USER_ID)).thenReturn(userGames);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> {
            Game game = inv.getArgument(0);
            game.setId(ids.incrementAndGet());
            createdGames.put(game.getId(), game);
            return game;
        });
        when(gameRepository.getReferenceById(any())).thenAnswer(inv -> createdGames.get(inv.<Long>getArgument(0)));
        when(gameRepository.existsByUserIdAndNameIgnoreCase(eq(USER_ID), any())).thenAnswer(inv ->
                createdGames.values().stream().anyMatch(g -> g.getName().equalsIgnoreCase(inv.getArgument(1))));
        when(gamePlatformLinkRepository.save(any(GamePlatformLink.class))).thenAnswer(inv -> {
            savedLinks.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    @Test
    void unknownTitleBecomesPendingGameWithItsConsoleLinkPlaytimeAndArtwork() {
        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID,
                library(title("PPSA01411_00", "Demon's Souls", ExternalPlatform.PSN_PS5, 2430)));

        assertThat(result).isEqualTo(new PsnSyncResultDto(1, 1, 0, 0, 0, 0, 0, false));
        ArgumentCaptor<Game> game = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(game.capture());
        assertThat(game.getValue().getReviewStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(game.getValue().getCategory()).isNull();
        assertThat(game.getValue().getCoverImageUrl()).isEqualTo("https://image.api.playstation.com/PPSA01411_00.png");
        assertThat(savedLinks).singleElement().satisfies(link -> {
            assertThat(link.getPlatform()).isEqualTo(ExternalPlatform.PSN_PS5);
            assertThat(link.getExternalId()).isEqualTo("PPSA01411_00");
            assertThat(link.getLastSyncedPlaytimeMinutes()).isEqualTo(2430L);
            assertThat(link.getLastPlayedAt()).isEqualTo(LAST_PLAYED);
        });
    }

    @Test
    void ps4AndPs5VersionsOfTheSameGameShareOnePendingGame() {
        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("PPSA01234_00", "Ghost of Tsushima", ExternalPlatform.PSN_PS5, 600),
                title("CUSA13323_00", "Ghost of Tsushima", ExternalPlatform.PSN_PS4, 1200)));

        assertThat(result.newGamesPending()).isEqualTo(1);
        assertThat(result.linkedExisting()).isEqualTo(1);
        verify(gameRepository).save(any(Game.class));
        assertThat(savedLinks).extracting(GamePlatformLink::getPlatform)
                .containsExactly(ExternalPlatform.PSN_PS5, ExternalPlatform.PSN_PS4);
        assertThat(savedLinks).extracting(link -> link.getGame().getId()).containsOnly(101L);
        verify(pendingGameEnricher).enqueue(USER_ID);
    }

    @Test
    void sameNameWithDifferentPsnConceptsBecomesTwoPendingGames() {
        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("CUSA02636_00", "Until Dawn™", ExternalPlatform.PSN_PS4, 600, 201L),
                title("PPSA21000_00", "Until Dawn™", ExternalPlatform.PSN_PS5, 300, 202L)));

        assertThat(result.newGamesPending()).isEqualTo(2);
        assertThat(result.linkedExisting()).isZero();
        assertThat(createdGames.values()).extracting(Game::getName, Game::getImportedName)
                .containsExactlyInAnyOrder(tuple("Until Dawn", "Until Dawn"), tuple("Until Dawn (PS5)", "Until Dawn"));
        assertThat(savedLinks).extracting(link -> link.getGame().getId()).doesNotHaveDuplicates();
    }

    @Test
    void sameNameAndSamePsnConceptShareOnePendingGame() {
        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("PPSA01234_00", "Ghost of Tsushima", ExternalPlatform.PSN_PS5, 600, 300L),
                title("CUSA13323_00", "Ghost of Tsushima", ExternalPlatform.PSN_PS4, 1200, 300L)));

        assertThat(result.newGamesPending()).isEqualTo(1);
        assertThat(result.linkedExisting()).isEqualTo(1);
        assertThat(savedLinks).extracting(link -> link.getGame().getId()).containsOnly(101L);
    }

    @Test
    void anUnknownConceptOnEitherSideKeepsTheNameMatch() {
        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("PPSA01234_00", "Ghost of Tsushima", ExternalPlatform.PSN_PS5, 600, 300L),
                title("CUSA13323_00", "Ghost of Tsushima", ExternalPlatform.PSN_PS4, 1200)));

        assertThat(result.newGamesPending()).isEqualTo(1);
        assertThat(result.linkedExisting()).isEqualTo(1);
    }

    @Test
    void aManualGameAttractsATitleWhateverItsConcept() {
        existingGame(10L, "Until Dawn", null);

        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID,
                library(title("PPSA21000_00", "Until Dawn", ExternalPlatform.PSN_PS5, 300, 202L)));

        assertThat(result.linkedExisting()).isEqualTo(1);
        assertThat(savedLinks).singleElement().extracting(link -> link.getGame().getId()).isEqualTo(10L);
    }

    @Test
    void aGameLinkedToAnotherConceptGetsASeparateGameAndRegionalVersionsFindIt() {
        Game original = existingGame(10L, "Until Dawn", null);
        GamePlatformLink ps4 = GamePlatformLink.builder()
                .userId(USER_ID).game(original).platform(ExternalPlatform.PSN_PS4).externalId("CUSA02636_00")
                .lastSyncedPlaytimeMinutes(600L).lastSyncedAt(LocalDateTime.now()).psnConceptId(201L).build();
        when(gamePlatformLinkRepository.findAllByUserIdAndPlatformInWithGame(USER_ID, PsnPlatforms.ALL))
                .thenReturn(List.of(ps4));

        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("CUSA02636_00", "Until Dawn", ExternalPlatform.PSN_PS4, 600, 201L),
                title("PPSA21000_00", "Until Dawn", ExternalPlatform.PSN_PS5, 300, 202L),
                title("PPSA21001_00", "Until Dawn", ExternalPlatform.PSN_PS5, 10, 202L)));

        assertThat(result.newGamesPending()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(createdGames.get(101L).getName()).isEqualTo("Until Dawn (PS5)");
        assertThat(savedLinks).singleElement().satisfies(link -> {
            assertThat(link.getGame().getId()).isEqualTo(101L);
            assertThat(link.getExternalId()).isEqualTo("PPSA21000_00");
        });
    }

    @Test
    void aThirdConceptWithTheSameNameAndConsoleIsNumbered() {
        importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("CUSA00001_00", "Doom", ExternalPlatform.PSN_PS4, 60, 1L),
                title("CUSA00002_00", "Doom", ExternalPlatform.PSN_PS4, 60, 2L),
                title("CUSA00003_00", "Doom", ExternalPlatform.PSN_PS4, 60, 3L)));

        assertThat(createdGames.values()).extracting(Game::getName)
                .containsExactlyInAnyOrder("Doom", "Doom (PS4)", "Doom (PS4) (2)");
    }

    @Test
    void storeNameWithTrademarksAndConsoleSuffixIsLinkedToTheSameGame() {
        existingGame(10L, "Marvel's Spider-Man", "https://example.com/mine.png");
        existingGame(11L, "Grand Theft Auto V", "https://example.com/gta.png");

        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("CUSA02299_00", "Marvel’s Spider-Man™", ExternalPlatform.PSN_PS4, 60),
                title("PPSA03420_00", "Grand Theft Auto V (PlayStation®5)", ExternalPlatform.PSN_PS5, 90)));

        assertThat(result.newGamesPending()).isZero();
        assertThat(result.linkedExisting()).isEqualTo(2);
        verify(gameRepository, never()).save(any(Game.class));
        verify(pendingGameEnricher).enqueue(USER_ID);
    }

    @Test
    void pendingGameIsStoredWithTheCleanNameAsNameAndImportedName() {
        importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("PPSA03420_00", "Ghost of Tsushima™: Director’s Cut (PlayStation®5)",
                        ExternalPlatform.PSN_PS5, 90)));

        ArgumentCaptor<Game> game = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(game.capture());
        assertThat(game.getValue().getName()).isEqualTo("Ghost of Tsushima: Director's Cut");
        assertThat(game.getValue().getImportedName()).isEqualTo("Ghost of Tsushima: Director's Cut");
    }

    @Test
    void titlesThatCleanToTheSameNameShareOnePendingGame() {
        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("PPSA01234_00", "Ratchet & Clank™ (PS5)", ExternalPlatform.PSN_PS5, 600),
                title("CUSA13323_00", "RATCHET & CLANK®", ExternalPlatform.PSN_PS4, 1200)));

        assertThat(result.newGamesPending()).isEqualTo(1);
        assertThat(result.linkedExisting()).isEqualTo(1);
        verify(gameRepository).save(any(Game.class));
        assertThat(createdGames.values()).singleElement()
                .extracting(Game::getName).isEqualTo("Ratchet & Clank");
    }

    @Test
    void titleThatIsOnlyMarksKeepsItsRawName() {
        importer.importLibrary(USER_ID, ACCOUNT_ID,
                library(title("CUSA00003_00", "®", ExternalPlatform.PSN_PS4, 60)));

        assertThat(createdGames.values()).singleElement().satisfies(game -> {
            assertThat(game.getName()).isEqualTo("®");
            assertThat(game.getImportedName()).isEqualTo("®");
        });
    }

    @Test
    void romanNumeralTitleLinksToTheGameTypedWithDigits() {
        existingGame(10L, "Hades 2", "https://example.com/hades.png");

        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID,
                library(title("PPSA20000_00", "Hades II", ExternalPlatform.PSN_PS5, 30)));

        assertThat(result.linkedExisting()).isEqualTo(1);
        assertThat(result.newGamesPending()).isZero();
    }

    @Test
    void enrichmentIsQueuedEvenWhenTheImportStopsHalfway() {
        when(psnLinkRepository.isLinkedTo(USER_ID, ACCOUNT_ID)).thenReturn(true, false);

        assertThatThrownBy(() -> importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("CUSA00001_00", "Bloodborne", ExternalPlatform.PSN_PS4, 60),
                title("CUSA00002_00", "Dreams", ExternalPlatform.PSN_PS4, 60))))
                .isInstanceOf(PsnIntegrationException.class);

        assertThat(createdGames).hasSize(1);
        verify(pendingGameEnricher).enqueue(USER_ID);
    }

    @Test
    void aNameWithoutKeyNeverLinksToAnotherGame() {
        existingGame(10L, "™", null);

        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID,
                library(title("CUSA00003_00", "®", ExternalPlatform.PSN_PS4, 60)));

        assertThat(result.linkedExisting()).isZero();
        assertThat(result.newGamesPending()).isEqualTo(1);
        assertThat(savedLinks).extracting(link -> link.getGame().getId()).doesNotContain(10L);
    }

    @Test
    void secondTitleOfTheSameConsoleAndNameIsSkipped() {
        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("CUSA00001_00", "Bloodborne", ExternalPlatform.PSN_PS4, 60),
                title("CUSA00002_00", "Bloodborne", ExternalPlatform.PSN_PS4, 30)));

        assertThat(result.newGamesPending()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(savedLinks).hasSize(1);
    }

    @Test
    void sameNameGameIsLinkedWithBaselineAndKeepsUserCover() {
        Game existing = existingGame(10L, "bloodborne", "https://example.com/mine.png");

        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID,
                library(title("CUSA00900_00", "Bloodborne", ExternalPlatform.PSN_PS4, 3000)));

        assertThat(result.linkedExisting()).isEqualTo(1);
        verify(gameRepository, never()).save(any(Game.class));
        assertThat(existing.getCoverImageUrl()).isEqualTo("https://example.com/mine.png");
        assertThat(savedLinks).singleElement().satisfies(link -> {
            assertThat(link.getGame()).isSameAs(existing);
            assertThat(link.getLastSyncedPlaytimeMinutes()).isEqualTo(3000L);
        });
    }

    @Test
    void sameNameGameWithoutCoverGetsPsnArtwork() {
        Game existing = existingGame(10L, "Bloodborne", null);

        importer.importLibrary(USER_ID, ACCOUNT_ID, library(title("CUSA00900_00", "Bloodborne", ExternalPlatform.PSN_PS4, 60)));

        assertThat(existing.getCoverImageUrl()).isEqualTo("https://image.api.playstation.com/CUSA00900_00.png");
    }

    @Test
    void ignoredTitleIsCountedAndNotImported() {
        when(psnIgnoredTitleRepository.findTitleIdsByUserId(USER_ID)).thenReturn(Set.of("CUSA00900_00"));

        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID,
                library(title("CUSA00900_00", "Bloodborne", ExternalPlatform.PSN_PS4, 60)));

        assertThat(result.ignored()).isEqualTo(1);
        assertThat(savedLinks).isEmpty();
    }

    @Test
    void linkedTitleOnlyUpdatesItsBaselineAndLastPlayed() {
        Game game = existingGame(10L, "Bloodborne", null);
        GamePlatformLink link = GamePlatformLink.builder()
                .userId(USER_ID).game(game).platform(ExternalPlatform.PSN_PS4).externalId("CUSA00900_00")
                .lastSyncedPlaytimeMinutes(60L).lastSyncedAt(LocalDateTime.now()).build();
        when(gamePlatformLinkRepository.findAllByUserIdAndPlatformInWithGame(USER_ID, PsnPlatforms.ALL))
                .thenReturn(List.of(link));
        when(externalPlaytimeWriter.applySyncedTotal(eq(link), eq(120L), any())).thenReturn(true);

        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID,
                library(title("CUSA00900_00", "Bloodborne", ExternalPlatform.PSN_PS4, 120)));

        assertThat(result).isEqualTo(new PsnSyncResultDto(1, 0, 0, 1, 0, 0, 0, false));
        assertThat(link.getLastPlayedAt()).isEqualTo(LAST_PLAYED);
        assertThat(savedLinks).isEmpty();
    }

    @Test
    void newLinkStoresThePsnConcept() {
        importer.importLibrary(USER_ID, ACCOUNT_ID,
                library(title("PPSA01411_00", "Demon's Souls", ExternalPlatform.PSN_PS5, 60, 10000177L)));

        assertThat(savedLinks).singleElement()
                .extracting(GamePlatformLink::getPsnConceptId).isEqualTo(10000177L);
    }

    @Test
    void linkedTitleBackfillsItsConceptButAMissingOneNeverErasesIt() {
        Game game = existingGame(10L, "Bloodborne", null);
        GamePlatformLink legacy = GamePlatformLink.builder()
                .userId(USER_ID).game(game).platform(ExternalPlatform.PSN_PS4).externalId("CUSA00900_00")
                .lastSyncedPlaytimeMinutes(60L).lastSyncedAt(LocalDateTime.now()).build();
        GamePlatformLink known = GamePlatformLink.builder()
                .userId(USER_ID).game(game).platform(ExternalPlatform.PSN_PS5).externalId("PPSA00900_00")
                .lastSyncedPlaytimeMinutes(60L).lastSyncedAt(LocalDateTime.now()).psnConceptId(77L).build();
        when(gamePlatformLinkRepository.findAllByUserIdAndPlatformInWithGame(USER_ID, PsnPlatforms.ALL))
                .thenReturn(List.of(legacy, known));

        importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("CUSA00900_00", "Bloodborne", ExternalPlatform.PSN_PS4, 60, 77L),
                title("PPSA00900_00", "Bloodborne", ExternalPlatform.PSN_PS5, 60)));

        assertThat(legacy.getPsnConceptId()).isEqualTo(77L);
        assertThat(known.getPsnConceptId()).isEqualTo(77L);
    }

    @Test
    void privateProfileChangesNothing() {
        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID, PsnPlayedGamesResult.privateProfile());

        assertThat(result.profilePrivate()).isTrue();
        verify(psnLinkRepository, never()).markSynced(any(), any(), any());
    }

    @Test
    void unlinkedDuringTheCallStopsWithLinkChanged() {
        when(psnLinkRepository.markSynced(eq(USER_ID), eq(ACCOUNT_ID), any())).thenReturn(0);

        assertThatThrownBy(() -> importer.importLibrary(USER_ID, ACCOUNT_ID,
                library(title("CUSA00900_00", "Bloodborne", ExternalPlatform.PSN_PS4, 60))))
                .isInstanceOf(PsnIntegrationException.class)
                .hasMessage(PsnIntegrationException.LINK_CHANGED);
    }

    @Test
    void failingTitleIsCountedAsErroredAndTheRestStillImports() {
        when(gameRepository.save(any(Game.class)))
                .thenThrow(new IllegalStateException("constraint"))
                .thenAnswer(inv -> {
                    Game game = inv.getArgument(0);
                    game.setId(ids.incrementAndGet());
                    return game;
                });

        PsnSyncResultDto result = importer.importLibrary(USER_ID, ACCOUNT_ID, library(
                title("CUSA00001_00", "Broken", ExternalPlatform.PSN_PS4, 60),
                title("CUSA00002_00", "Fine", ExternalPlatform.PSN_PS4, 60)));

        assertThat(result.errored()).isEqualTo(1);
        assertThat(result.newGamesPending()).isEqualTo(1);
    }

    private Game existingGame(Long id, String name, String cover) {
        Game game = Game.builder()
                .id(id)
                .userId(USER_ID)
                .name(name)
                .coverImageUrl(cover)
                .reviewStatus(ReviewStatus.CONFIRMED)
                .genres(new HashSet<>())
                .build();
        userGames.add(game);
        createdGames.put(id, game);
        return game;
    }

    private static PsnPlayedTitle title(String titleId, String name, ExternalPlatform platform, long minutes) {
        return new PsnPlayedTitle(titleId, name, "https://image.api.playstation.com/" + titleId + ".png",
                platform, 1, minutes, null, LAST_PLAYED);
    }

    private static PsnPlayedTitle title(String titleId, String name, ExternalPlatform platform, long minutes,
                                        Long conceptId) {
        return new PsnPlayedTitle(titleId, name, "https://image.api.playstation.com/" + titleId + ".png",
                platform, 1, minutes, null, LAST_PLAYED, conceptId);
    }

    private static PsnPlayedGamesResult library(PsnPlayedTitle... titles) {
        return new PsnPlayedGamesResult(false, List.of(titles));
    }
}
