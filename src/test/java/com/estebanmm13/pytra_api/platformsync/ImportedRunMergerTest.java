package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.service.experienceService.ExperienceService;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportedRunMergerTest {

    private static final long USER_ID = 1L;
    private static final long GAME_ID = 10L;
    private static final long MANUAL_RUN_ID = 100L;
    private static final long IMPORTED_RUN_ID = 200L;

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GamePlatformLinkRepository gamePlatformLinkRepository = mock(GamePlatformLinkRepository.class);
    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final ExperienceService experienceService = mock(ExperienceService.class);
    private final DiaryEntryGameRepository diaryEntryGameRepository = mock(DiaryEntryGameRepository.class);
    private final ImportedRunMerger merger = new ImportedRunMerger(gameRepository, gamePlatformLinkRepository,
            experienceRepository, experienceService, diaryEntryGameRepository, TransactionOperations.withoutTransaction());

    private Game game;
    private Experience manual;
    private Experience imported;
    private GamePlatformLink link;
    private final List<Experience> runs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        game = Game.builder().id(GAME_ID).userId(USER_ID).name("Saros")
                .category(GameCategory.SINGLEPLAYER).reviewStatus(ReviewStatus.CONFIRMED).build();
        manual = run(MANUAL_RUN_ID, "Run 1", "PS5", 15.0, null);
        imported = run(IMPORTED_RUN_ID, ImportedRunSource.PSN_PS5.runLabel(), "PS5", 1.3,
                ImportedRunSource.PSN_PS5.note());
        runs.addAll(List.of(manual, imported));
        link = GamePlatformLink.builder().id(5L).userId(USER_ID).game(game).platform(ExternalPlatform.PSN_PS5)
                .externalId("PPSA00001_00").experienceId(IMPORTED_RUN_ID).build();

        when(gamePlatformLinkRepository.findAllByUserIdAndPlatformInWithGame(eq(USER_ID), any()))
                .thenReturn(List.of(link));
        when(gamePlatformLinkRepository.findAllByUserIdAndGameId(USER_ID, GAME_ID)).thenReturn(List.of(link));
        when(gameRepository.findForUpdateByIdAndUserId(GAME_ID, USER_ID)).thenAnswer(inv -> Optional.of(game));
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID)).thenAnswer(inv -> List.copyOf(runs));
        // The real addHours adds to the run and the real delete removes it.
        doAnswer(inv -> {
            Experience target = runs.stream().filter(r -> r.getId().equals(inv.getArgument(0))).findFirst().orElseThrow();
            target.setHours(target.getHours() + inv.<BigDecimal>getArgument(1).doubleValue());
            return null;
        }).when(experienceService).addHours(anyLong(), any(), eq(USER_ID));
        doAnswer(inv -> runs.remove(inv.<Experience>getArgument(0))).when(experienceRepository).delete(any(Experience.class));
    }

    @Test
    void untouchedImportedRunIsFoldedIntoTheInProgressRunOnTheSamePlatform() {
        int merged = merger.mergeAll(USER_ID);

        assertThat(merged).isEqualTo(1);
        verify(experienceService).addHours(MANUAL_RUN_ID, BigDecimal.valueOf(1.3), USER_ID);
        assertThat(manual.getHours()).isCloseTo(16.3, within(1e-9));
        assertThat(link.getExperienceId()).isEqualTo(MANUAL_RUN_ID);
        verify(diaryEntryGameRepository).repointRun(IMPORTED_RUN_ID, MANUAL_RUN_ID);
        verify(experienceRepository).delete(imported);
        assertThat(runs).containsExactly(manual);
    }

    @Test
    void secondSyncChangesNothing() {
        merger.mergeAll(USER_ID);
        merger.mergeAll(USER_ID);

        verify(experienceService).addHours(anyLong(), any(), eq(USER_ID));
        verify(experienceRepository).delete(any(Experience.class));
        assertThat(manual.getHours()).isCloseTo(16.3, within(1e-9));
    }

    @Test
    void importedRunTheUserEditedIsKept() {
        assertNoMerge(() -> imported.setRating(new BigDecimal("8.5")));
        assertNoMerge(() -> imported.setSummary("Great"));
        assertNoMerge(() -> imported.setNotes("My own note"));
        assertNoMerge(() -> imported.setStartDate(LocalDate.of(2026, 1, 1)));
        assertNoMerge(() -> imported.setPlatinum(true));
        assertNoMerge(() -> imported.setFullCompletion(true));
        assertNoMerge(() -> imported.setReplay(true));
        assertNoMerge(() -> imported.setStatus(ExperienceStatus.COMPLETADO));
        assertNoMerge(() -> imported.setRunLabel("Partida PS5"));
    }

    @Test
    void noMergeWithoutExactlyOneInProgressRunOnThatPlatform() {
        assertNoMerge(() -> manual.setStatus(ExperienceStatus.COMPLETADO));
        assertNoMerge(() -> manual.getPlatform().setLegacyCode("PS4"));
        assertNoMerge(() -> runs.add(run(300L, "Run 2", "PS5", 4.0, null)));
    }

    @Test
    void onlyConfirmedSingleplayerGamesAreHealed() {
        assertNoMerge(() -> game.setCategory(GameCategory.HYBRID));
        assertNoMerge(() -> game.setReviewStatus(ReviewStatus.PENDING_REVIEW));
    }

    @Test
    void failingGameIsLoggedAndDoesNotFailTheSync() {
        when(gameRepository.findForUpdateByIdAndUserId(GAME_ID, USER_ID)).thenThrow(new IllegalStateException("boom"));

        assertThat(merger.mergeAll(USER_ID)).isZero();
    }

    /** Applies one change to a fresh fixture and checks that nothing is merged. */
    private void assertNoMerge(Runnable change) {
        runs.clear();
        setUpFresh(change);
        assertThat(merger.mergeAll(USER_ID)).isZero();
        verify(experienceService, never()).addHours(anyLong(), any(), any());
        verify(experienceRepository, never()).delete(any(Experience.class));
        assertThat(link.getExperienceId()).isEqualTo(IMPORTED_RUN_ID);
    }

    private void setUpFresh(Runnable change) {
        game.setCategory(GameCategory.SINGLEPLAYER);
        game.setReviewStatus(ReviewStatus.CONFIRMED);
        manual = run(MANUAL_RUN_ID, "Run 1", "PS5", 15.0, null);
        imported = run(IMPORTED_RUN_ID, ImportedRunSource.PSN_PS5.runLabel(), "PS5", 1.3,
                ImportedRunSource.PSN_PS5.note());
        runs.addAll(List.of(manual, imported));
        change.run();
    }

    private static Experience run(Long id, String label, String platformCode, double hours, String notes) {
        return Experience.builder()
                .id(id)
                .userId(USER_ID)
                .gameId(GAME_ID)
                .runLabel(label)
                .status(ExperienceStatus.EN_CURSO)
                .hours(hours)
                .platform(Platform.builder().name(platformCode).legacyCode(platformCode).build())
                .platinum(false)
                .replay(false)
                .notes(notes)
                .build();
    }
}
