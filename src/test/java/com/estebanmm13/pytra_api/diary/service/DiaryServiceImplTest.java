package com.estebanmm13.pytra_api.diary.service;

import com.estebanmm13.pytra_api.diary.dto.DiaryDayRequestDto;
import com.estebanmm13.pytra_api.diary.dto.DiaryDayResponseDto;
import com.estebanmm13.pytra_api.diary.dto.DiaryGameRequestDto;
import com.estebanmm13.pytra_api.diary.model.DiaryEntry;
import com.estebanmm13.pytra_api.diary.model.DiaryEntryGame;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryRepository;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.service.experienceService.ExperienceService;
import com.estebanmm13.pytra_api.experiences.service.onlinePlaytimeService.OnlinePlaytimeService;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DiaryServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long GAME_ID = 3L;
    private static final Long RUN_A = 11L;
    private static final Long RUN_B = 12L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    private final DiaryEntryRepository diaryEntryRepository = mock(DiaryEntryRepository.class);
    private final DiaryEntryGameRepository diaryEntryGameRepository = mock(DiaryEntryGameRepository.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final GamePlatformLinkRepository gamePlatformLinkRepository = mock(GamePlatformLinkRepository.class);
    private final ExperienceService experienceService = mock(ExperienceService.class);
    private final OnlinePlaytimeService onlinePlaytimeService = mock(OnlinePlaytimeService.class);
    private final DiaryServiceImpl service = new DiaryServiceImpl(diaryEntryRepository, diaryEntryGameRepository,
            gameRepository, experienceRepository, gamePlatformLinkRepository, experienceService, onlinePlaytimeService);

    @BeforeEach
    void setUp() {
        when(diaryEntryRepository.findByUserIdAndEntryDate(USER_ID, DAY)).thenReturn(Optional.empty());
        when(diaryEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gamePlatformLinkRepository.findGameIdsByUserIdAndPlatformIn(eq(USER_ID), any())).thenReturn(Set.of());
    }

    @Test
    void newDayAddsTheHoursToTheOnlyRunInProgress() {
        ownedGame(GameCategory.SINGLEPLAYER);
        runs(run(RUN_A, ExperienceStatus.EN_CURSO), run(RUN_B, ExperienceStatus.COMPLETADO));

        DiaryDayResponseDto day = service.saveDay(DAY, day(game(null, "2.5")), USER_ID).orElseThrow();

        verify(experienceService).addHours(RUN_A, new BigDecimal("2.5"), USER_ID);
        assertThat(day.totalHours()).isEqualByComparingTo("2.5");
        assertThat(day.games()).singleElement().satisfies(game -> {
            assertThat(game.experienceId()).isEqualTo(RUN_A);
            assertThat(game.countsHours()).isTrue();
        });
    }

    @Test
    void editingTheSameRunAppliesOnlyTheDifference() {
        ownedGame(GameCategory.SINGLEPLAYER);
        runs(run(RUN_A, ExperienceStatus.EN_CURSO));
        existingDay(line(RUN_A, "2.5", true));

        service.saveDay(DAY, day(game(null, "4")), USER_ID);

        verify(experienceService).addHours(RUN_A, new BigDecimal("1.5"), USER_ID);
    }

    @Test
    void unchangedHoursMoveNothing() {
        ownedGame(GameCategory.SINGLEPLAYER);
        runs(run(RUN_A, ExperienceStatus.EN_CURSO));
        existingDay(line(RUN_A, "2.50", true));

        service.saveDay(DAY, day(game(null, "2.5")), USER_ID);

        verifyNoInteractions(experienceService);
    }

    @Test
    void changingTheRunMovesTheHours() {
        ownedGame(GameCategory.SINGLEPLAYER);
        runs(run(RUN_A, ExperienceStatus.EN_CURSO), run(RUN_B, ExperienceStatus.EN_CURSO));
        DiaryEntry entry = existingDay(line(RUN_A, "2", true));

        service.saveDay(DAY, day(game(RUN_B, "3")), USER_ID);

        verify(experienceService).addHours(RUN_A, new BigDecimal("-2"), USER_ID);
        verify(experienceService).addHours(RUN_B, new BigDecimal("3"), USER_ID);
        assertThat(entry.getGames()).singleElement().satisfies(line -> {
            assertThat(line.getExperienceId()).isEqualTo(RUN_B);
            assertThat(line.getHours()).isEqualByComparingTo("3");
        });
    }

    @Test
    void resavingWithoutARunKeepsTheLinesRunEvenWhenSeveralAreInProgress() {
        ownedGame(GameCategory.SINGLEPLAYER);
        runs(run(RUN_A, ExperienceStatus.EN_CURSO), run(RUN_B, ExperienceStatus.EN_CURSO));
        existingDay(line(RUN_B, "1", true));

        service.saveDay(DAY, day(game(null, "1.25")), USER_ID);

        verify(experienceService).addHours(RUN_B, new BigDecimal("0.25"), USER_ID);
    }

    @Test
    void removingAGameFromTheDaySubtractsItsHours() {
        ownedGame(GameCategory.SINGLEPLAYER);
        DiaryEntry entry = existingDay(line(RUN_A, "2", true));
        DiaryDayRequestDto noteOnly = day();
        noteOnly.setNote("Rest day");

        service.saveDay(DAY, noteOnly, USER_ID);

        verify(experienceService).addHours(RUN_A, new BigDecimal("-2"), USER_ID);
        assertThat(entry.getGames()).isEmpty();
        assertThat(entry.getNote()).isEqualTo("Rest day");
    }

    @Test
    void deletingTheDaySubtractsEveryCountedLine() {
        DiaryEntry entry = existingDay(line(RUN_A, "2", true));

        service.deleteDay(DAY, USER_ID);

        verify(experienceService).addHours(RUN_A, new BigDecimal("-2"), USER_ID);
        verify(diaryEntryRepository).delete(entry);
    }

    @Test
    void anEmptyDayIsDeleted() {
        DiaryEntry entry = existingDay(line(RUN_A, "2", true));

        Optional<DiaryDayResponseDto> result = service.saveDay(DAY, day(), USER_ID);

        assertThat(result).isEmpty();
        verify(experienceService).addHours(RUN_A, new BigDecimal("-2"), USER_ID);
        verify(diaryEntryRepository).delete(entry);
    }

    @Test
    void deletingAMissingDayIsNotFound() {
        assertThatThrownBy(() -> service.deleteDay(DAY, USER_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void onlineGamesAddToTheOnlinePlaytime() {
        ownedGame(GameCategory.ONLINE);

        DiaryDayResponseDto day = service.saveDay(DAY, day(game(null, "1.75")), USER_ID).orElseThrow();

        verify(onlinePlaytimeService).addHours(GAME_ID, new BigDecimal("1.75"), USER_ID);
        verifyNoInteractions(experienceService);
        assertThat(day.games().get(0).countsHours()).isTrue();
        assertThat(day.games().get(0).experienceId()).isNull();
    }

    @Test
    void editingAnOnlineGameAppliesTheDifferenceToTheOnlinePlaytime() {
        ownedGame(GameCategory.ONLINE);
        existingDay(line(null, "3", true));

        service.saveDay(DAY, day(game(null, "1")), USER_ID);

        verify(onlinePlaytimeService).addHours(GAME_ID, new BigDecimal("-2"), USER_ID);
    }

    @Test
    void syncedGamesAreRecordedWithoutAddingHours() {
        ownedGame(GameCategory.SINGLEPLAYER);
        when(gamePlatformLinkRepository.findGameIdsByUserIdAndPlatformIn(eq(USER_ID), any())).thenReturn(Set.of(GAME_ID));

        DiaryDayResponseDto day = service.saveDay(DAY, day(game(null, "2")), USER_ID).orElseThrow();

        verifyNoInteractions(experienceService, onlinePlaytimeService);
        assertThat(day.games().get(0).countsHours()).isFalse();
        assertThat(day.games().get(0).synced()).isTrue();
    }

    @Test
    void aGameSyncedAfterItsLineWasCountedKeepsTheCountedHours() {
        ownedGame(GameCategory.SINGLEPLAYER);
        runs(run(RUN_A, ExperienceStatus.EN_CURSO));
        DiaryEntry entry = existingDay(line(RUN_A, "2", true));
        when(gamePlatformLinkRepository.findGameIdsByUserIdAndPlatformIn(eq(USER_ID), any())).thenReturn(Set.of(GAME_ID));

        service.saveDay(DAY, day(game(null, "2")), USER_ID);

        verifyNoInteractions(experienceService);
        assertThat(entry.getGames()).singleElement().satisfies(line -> assertThat(line.isCountsHours()).isTrue());
    }

    @Test
    void editingALineCountedBeforeTheGameWasSyncedAppliesTheDifference() {
        ownedGame(GameCategory.SINGLEPLAYER);
        runs(run(RUN_A, ExperienceStatus.EN_CURSO));
        existingDay(line(RUN_A, "2", true));
        when(gamePlatformLinkRepository.findGameIdsByUserIdAndPlatformIn(eq(USER_ID), any())).thenReturn(Set.of(GAME_ID));

        service.saveDay(DAY, day(game(null, "3")), USER_ID);

        verify(experienceService).addHours(RUN_A, new BigDecimal("1"), USER_ID);
    }

    @Test
    void aLineNotCountedForASyncedGameStaysUncounted() {
        ownedGame(GameCategory.SINGLEPLAYER);
        DiaryEntry entry = existingDay(line(RUN_A, "2", false));
        when(gamePlatformLinkRepository.findGameIdsByUserIdAndPlatformIn(eq(USER_ID), any())).thenReturn(Set.of(GAME_ID));
        when(experienceRepository.findByIdAndUserId(RUN_A, USER_ID)).thenReturn(Optional.of(run(RUN_A, ExperienceStatus.EN_CURSO)));

        service.saveDay(DAY, day(game(RUN_A, "4")), USER_ID);

        verifyNoInteractions(experienceService, onlinePlaytimeService);
        assertThat(entry.getGames()).singleElement().satisfies(line -> assertThat(line.isCountsHours()).isFalse());
    }

    @Test
    void severalRunsInProgressRequireTheRunAndChangeNothing() {
        ownedGame(GameCategory.SINGLEPLAYER);
        runs(run(RUN_A, ExperienceStatus.EN_CURSO), run(RUN_B, ExperienceStatus.EN_CURSO));

        assertThatThrownBy(() -> service.saveDay(DAY, day(game(null, "2")), USER_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.DIARY_RUN_REQUIRED)
                .satisfies(e -> assertThat(((InvalidRequestException) e).getFieldErrors())
                        .containsEntry("games[0].experienceId", InvalidRequestException.DIARY_RUN_REQUIRED));
        verifyNoInteractions(experienceService);
        verify(diaryEntryRepository, never()).save(any());
    }

    @Test
    void aGameWithoutRunsRequiresTheRun() {
        ownedGame(GameCategory.HYBRID);
        runs();

        assertThatThrownBy(() -> service.saveDay(DAY, day(game(null, "2")), USER_ID))
                .hasMessage(InvalidRequestException.DIARY_RUN_REQUIRED);
    }

    @Test
    void aRunOfAnotherGameIsRejected() {
        ownedGame(GameCategory.SINGLEPLAYER);
        runs(run(RUN_A, ExperienceStatus.EN_CURSO));

        assertThatThrownBy(() -> service.saveDay(DAY, day(game(99L, "2")), USER_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.INVALID_DIARY_RUN);
        verifyNoInteractions(experienceService);
    }

    @Test
    void aDetachedLineAddsItsHoursAgainWithoutSubtracting() {
        ownedGame(GameCategory.SINGLEPLAYER);
        runs(run(RUN_B, ExperienceStatus.EN_CURSO));
        existingDay(line(null, "2", false));

        service.saveDay(DAY, day(game(null, "2")), USER_ID);

        verify(experienceService).addHours(RUN_B, new BigDecimal("2"), USER_ID);
        verify(experienceService, never()).addHours(eq(RUN_A), any(), anyLong());
        verifyNoInteractions(onlinePlaytimeService);
    }

    @Test
    void aForeignGameIsNotFound() {
        when(gameRepository.findByIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveDay(DAY, day(game(null, "1")), USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theSameGameTwiceIsRejected() {
        assertThatThrownBy(() -> service.saveDay(DAY, day(game(null, "1"), game(null, "2")), USER_ID))
                .hasMessage(InvalidRequestException.DUPLICATE_DIARY_GAME);
    }

    @Test
    void datesOutsideTheRunBoundsAreRejected() {
        assertThatThrownBy(() -> service.saveDay(LocalDate.of(1969, 12, 31), day(game(null, "1")), USER_ID))
                .hasMessage(InvalidRequestException.INVALID_DIARY_DATE);
        assertThatThrownBy(() -> service.saveDay(LocalDate.now().plusDays(2), day(game(null, "1")), USER_ID))
                .hasMessage(InvalidRequestException.INVALID_DIARY_DATE);
    }

    @Test
    void rangesMustBeOrderedAndBounded() {
        assertThatThrownBy(() -> service.findRange(DAY, DAY.minusDays(1), USER_ID))
                .hasMessage(InvalidRequestException.INVALID_DIARY_RANGE);
        assertThatThrownBy(() -> service.findRange(DAY, DAY.plusDays(DiaryServiceImpl.MAX_RANGE_DAYS), USER_ID))
                .hasMessage(InvalidRequestException.INVALID_DIARY_RANGE);
    }

    @Test
    void rangeLeavesOutDaysLeftEmpty() {
        DiaryEntry empty = DiaryEntry.builder().userId(USER_ID).entryDate(DAY).build();
        DiaryEntry withNote = DiaryEntry.builder().userId(USER_ID).entryDate(DAY.plusDays(1)).note("Hi").build();
        when(diaryEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(USER_ID, DAY, DAY.plusDays(30)))
                .thenReturn(List.of(empty, withNote));

        List<DiaryDayResponseDto> days = service.findRange(DAY, DAY.plusDays(30), USER_ID);

        assertThat(days).extracting(DiaryDayResponseDto::date).containsExactly(DAY.plusDays(1));
    }

    @Test
    void resavingAnUnchangedAutoLineKeepsItAutoAndMovesNothing() {
        // Not synced any more and with one run in progress: a user line would count, an auto line never does.
        ownedGame(GameCategory.SINGLEPLAYER);
        runs(run(RUN_A, ExperienceStatus.EN_CURSO));
        DiaryEntry entry = existingDay(autoLine(RUN_A, "1.50"));

        DiaryDayResponseDto day = service.saveDay(DAY, day(game(null, "1.5")), USER_ID).orElseThrow();

        verifyNoInteractions(experienceService, onlinePlaytimeService);
        assertThat(entry.getGames()).singleElement().satisfies(line -> {
            assertThat(line.isAutoSynced()).isTrue();
            assertThat(line.isCountsHours()).isFalse();
            assertThat(line.getExperienceId()).isEqualTo(RUN_A);
        });
        assertThat(day.games()).singleElement().satisfies(game -> assertThat(game.autoSynced()).isTrue());
    }

    @Test
    void editingAnAutoLineMakesItTheUsersButStillRecordOnly() {
        ownedGame(GameCategory.ONLINE);
        DiaryEntry entry = existingDay(autoLine(null, "1"));

        service.saveDay(DAY, day(game(null, "3")), USER_ID);

        verifyNoInteractions(experienceService, onlinePlaytimeService);
        assertThat(entry.getGames()).singleElement().satisfies(line -> {
            assertThat(line.isAutoSynced()).isFalse();
            assertThat(line.isCountsHours()).isFalse();
            assertThat(line.getHours()).isEqualByComparingTo("3");
        });
    }

    @Test
    void removingAnAutoLineSubtractsNothing() {
        ownedGame(GameCategory.ONLINE);
        existingDay(autoLine(null, "2"));
        DiaryDayRequestDto noteOnly = day();
        noteOnly.setNote("Rest day");

        service.saveDay(DAY, noteOnly, USER_ID);

        verifyNoInteractions(experienceService, onlinePlaytimeService);
    }

    private static DiaryEntryGame autoLine(Long experienceId, String hours) {
        DiaryEntryGame line = line(experienceId, hours, false);
        line.setAutoSynced(true);
        return line;
    }

    private Game ownedGame(GameCategory category) {
        Game game = new Game();
        game.setId(GAME_ID);
        game.setUserId(USER_ID);
        game.setName("Hades");
        game.setCategory(category);
        when(gameRepository.findByIdAndUserId(GAME_ID, USER_ID)).thenReturn(Optional.of(game));
        when(gameRepository.findAllById(any())).thenReturn(List.of(game));
        return game;
    }

    private void runs(Experience... runs) {
        when(experienceRepository.findAllByGameIdAndUserId(GAME_ID, USER_ID)).thenReturn(List.of(runs));
    }

    private static Experience run(Long id, ExperienceStatus status) {
        return Experience.builder().id(id).userId(USER_ID).gameId(GAME_ID).runLabel("Run " + id).status(status)
                .hours(0.0).build();
    }

    private DiaryEntry existingDay(DiaryEntryGame... lines) {
        DiaryEntry entry = DiaryEntry.builder().id(1L).userId(USER_ID).entryDate(DAY).games(new ArrayList<>()).build();
        for (DiaryEntryGame line : lines) {
            line.setEntry(entry);
            entry.getGames().add(line);
        }
        when(diaryEntryRepository.findByUserIdAndEntryDate(USER_ID, DAY)).thenReturn(Optional.of(entry));
        return entry;
    }

    private static DiaryEntryGame line(Long experienceId, String hours, boolean countsHours) {
        return DiaryEntryGame.builder().gameId(GAME_ID).experienceId(experienceId).hours(new BigDecimal(hours))
                .countsHours(countsHours).build();
    }

    private static DiaryGameRequestDto game(Long experienceId, String hours) {
        DiaryGameRequestDto game = new DiaryGameRequestDto();
        game.setGameId(GAME_ID);
        game.setExperienceId(experienceId);
        game.setHours(new BigDecimal(hours));
        return game;
    }

    private static DiaryDayRequestDto day(DiaryGameRequestDto... games) {
        DiaryDayRequestDto day = new DiaryDayRequestDto();
        day.setGames(new ArrayList<>(List.of(games)));
        return day;
    }
}
