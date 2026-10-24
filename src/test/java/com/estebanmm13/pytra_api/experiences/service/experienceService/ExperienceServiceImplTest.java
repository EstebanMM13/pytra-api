package com.estebanmm13.pytra_api.experiences.service.experienceService;

import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperiencePatchDto;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceRequestDto;
import com.estebanmm13.pytra_api.experiences.mapper.ExperienceMapper;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.service.hybridPlaytime.HybridPlaytimeReconciler;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platforms.service.PlatformService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExperienceServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long GAME_ID = 3L;
    private static final Long RUN_ID = 11L;

    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final PlatformService platformService = mock(PlatformService.class);
    private final HybridPlaytimeReconciler hybridPlaytimeReconciler = mock(HybridPlaytimeReconciler.class);
    private final DiaryEntryGameRepository diaryEntryGameRepository = mock(DiaryEntryGameRepository.class);
    private final ExperienceServiceImpl service = new ExperienceServiceImpl(
            experienceRepository, gameRepository, mock(ExperienceMapper.class), platformService, hybridPlaytimeReconciler,
            diaryEntryGameRepository);

    @Test
    void newRunForAnOnlineGameIsRejected() {
        ownedGame(GameCategory.ONLINE);

        assertThatThrownBy(() -> service.create(GAME_ID, request(), USER_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.ONLINE_GAME_RUN);
        verify(experienceRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = GameCategory.class, names = {"SINGLEPLAYER", "HYBRID"})
    void newRunForOtherCategoriesIsSaved(GameCategory category) {
        ownedGame(category);

        service.create(GAME_ID, request(), USER_ID);

        verify(experienceRepository).save(any());
    }

    @Test
    void createReconcilesHybridHoursAroundTheSave() {
        Game game = ownedGame(GameCategory.HYBRID);
        when(hybridPlaytimeReconciler.shareBeforeRunChange(game)).thenReturn(60.0);

        service.create(GAME_ID, request(), USER_ID);

        InOrder order = inOrder(hybridPlaytimeReconciler, experienceRepository);
        order.verify(hybridPlaytimeReconciler).shareBeforeRunChange(game);
        order.verify(experienceRepository).save(any());
        order.verify(hybridPlaytimeReconciler).afterRunChange(game, 60.0);
    }

    @Test
    void updateReconcilesHybridHoursAroundTheSave() {
        Game game = ownedGame(GameCategory.HYBRID);
        Experience run = ownedRun();
        when(hybridPlaytimeReconciler.shareBeforeRunChange(game)).thenReturn(60.0);

        service.update(RUN_ID, request(), USER_ID);

        InOrder order = inOrder(hybridPlaytimeReconciler, experienceRepository);
        order.verify(hybridPlaytimeReconciler).shareBeforeRunChange(game);
        order.verify(experienceRepository).save(run);
        order.verify(hybridPlaytimeReconciler).afterRunChange(game, 60.0);
    }

    @Test
    void patchChangesOnlyTheSentFiguresAndReconcilesHybridHours() {
        Game game = ownedGame(GameCategory.HYBRID);
        Experience run = ownedRun();
        run.setRunLabel("Run 1");
        run.setRating(new BigDecimal("7"));
        when(hybridPlaytimeReconciler.shareBeforeRunChange(game)).thenReturn(40.0);
        ExperiencePatchDto patch = new ExperiencePatchDto();
        patch.setHours(12.5);

        service.patch(RUN_ID, patch, USER_ID);

        assertThat(run.getHours()).isEqualTo(12.5);
        assertThat(run.getRating()).isEqualByComparingTo("7");
        assertThat(run.getRunLabel()).isEqualTo("Run 1");
        InOrder order = inOrder(hybridPlaytimeReconciler, experienceRepository);
        order.verify(hybridPlaytimeReconciler).shareBeforeRunChange(game);
        order.verify(experienceRepository).save(run);
        order.verify(hybridPlaytimeReconciler).afterRunChange(game, 40.0);
    }

    @Test
    void patchSetsOrClearsTheRatingOnlyWhenSentAndKeepsTheHours() {
        ownedGame(GameCategory.SINGLEPLAYER);
        Experience run = ownedRun();
        ExperiencePatchDto rate = new ExperiencePatchDto();
        rate.setRating(new BigDecimal("9.25"));

        service.patch(RUN_ID, rate, USER_ID);
        assertThat(run.getRating()).isEqualByComparingTo("9.25");
        assertThat(run.getHours()).isEqualTo(1.0);

        ExperiencePatchDto clear = new ExperiencePatchDto();
        clear.setRating(null);
        service.patch(RUN_ID, clear, USER_ID);
        assertThat(run.getRating()).isNull();
        assertThat(run.getHours()).isEqualTo(1.0);
    }

    @Test
    void patchOfAnExistingRunOfAnOnlineGameIsAllowed() {
        ownedGame(GameCategory.ONLINE);
        Experience run = ownedRun();
        ExperiencePatchDto patch = new ExperiencePatchDto();
        patch.setHours(4.0);

        service.patch(RUN_ID, patch, USER_ID);

        verify(experienceRepository).save(run);
    }

    @Test
    void patchOfAForeignRunIsNotFound() {
        when(experienceRepository.findByIdAndUserId(RUN_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patch(RUN_ID, new ExperiencePatchDto(), USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(experienceRepository, never()).save(any());
    }

    @Test
    void deleteReconcilesHybridHoursAroundTheDelete() {
        Game game = ownedGame(GameCategory.HYBRID);
        Experience run = ownedRun();
        when(hybridPlaytimeReconciler.shareBeforeRunChange(game)).thenReturn(20.0);

        service.delete(RUN_ID, USER_ID);

        InOrder order = inOrder(hybridPlaytimeReconciler, experienceRepository);
        order.verify(hybridPlaytimeReconciler).shareBeforeRunChange(game);
        order.verify(experienceRepository).delete(run);
        order.verify(hybridPlaytimeReconciler).afterRunChange(game, 20.0);
    }

    @Test
    void deleteDetachesTheRunsDiaryLinesBeforeDeletingIt() {
        ownedGame(GameCategory.SINGLEPLAYER);
        Experience run = ownedRun();

        service.delete(RUN_ID, USER_ID);

        InOrder order = inOrder(diaryEntryGameRepository, experienceRepository);
        order.verify(diaryEntryGameRepository).detachRun(RUN_ID);
        order.verify(experienceRepository).delete(run);
    }

    @Test
    void addHoursAddsAndSubtractsThroughTheReconciledSave() {
        Game game = ownedGame(GameCategory.HYBRID);
        Experience run = ownedRun();
        when(hybridPlaytimeReconciler.shareBeforeRunChange(game)).thenReturn(5.0);

        service.addHours(RUN_ID, new BigDecimal("2.25"), USER_ID);
        assertThat(run.getHours()).isEqualTo(3.25);

        service.addHours(RUN_ID, new BigDecimal("-0.1"), USER_ID);
        assertThat(run.getHours()).isEqualTo(3.15);

        InOrder order = inOrder(hybridPlaytimeReconciler, experienceRepository);
        order.verify(hybridPlaytimeReconciler).shareBeforeRunChange(game);
        order.verify(experienceRepository).save(run);
        order.verify(hybridPlaytimeReconciler).afterRunChange(game, 5.0);
    }

    @Test
    void addHoursNeverGoesBelowZero() {
        ownedGame(GameCategory.SINGLEPLAYER);
        Experience run = ownedRun();

        service.addHours(RUN_ID, new BigDecimal("-5"), USER_ID);

        assertThat(run.getHours()).isEqualTo(0.0);
    }

    private Experience ownedRun() {
        Experience run = Experience.builder().id(RUN_ID).userId(USER_ID).gameId(GAME_ID).hours(1.0).build();
        when(experienceRepository.findByIdAndUserId(RUN_ID, USER_ID)).thenReturn(Optional.of(run));
        return run;
    }

    private Game ownedGame(GameCategory category) {
        Game game = new Game();
        game.setCategory(category);
        when(gameRepository.findByIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(game));
        return game;
    }

    private static ExperienceRequestDto request() {
        ExperienceRequestDto request = new ExperienceRequestDto();
        request.setRunLabel("Run 1");
        request.setPlatformId(1L);
        return request;
    }
}
