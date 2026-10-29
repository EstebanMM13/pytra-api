package com.estebanmm13.pytra_api.diary.service;

import com.estebanmm13.pytra_api.diary.model.DiaryEntry;
import com.estebanmm13.pytra_api.diary.model.DiaryEntryGame;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncedPlayRecorderTest {

    private static final Long USER_ID = 7L;
    private static final Long GAME_ID = 3L;
    private static final Long OTHER_GAME_ID = 4L;
    private static final Long RUN = 11L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 29);

    private final DiaryEntryRepository diaryEntryRepository = mock(DiaryEntryRepository.class);
    private final SyncedPlayRecorder recorder = new SyncedPlayRecorder(diaryEntryRepository);

    @BeforeEach
    void setUp() {
        when(diaryEntryRepository.findByUserIdAndEntryDate(USER_ID, DAY)).thenReturn(Optional.empty());
        when(diaryEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void hoursOfRoundsMinutesToTwoDecimalsAndNeverGoesNegative() {
        assertThat(SyncedPlayRecorder.hoursOf(90)).isEqualByComparingTo("1.50");
        assertThat(SyncedPlayRecorder.hoursOf(1)).isEqualByComparingTo("0.02");
        assertThat(SyncedPlayRecorder.hoursOf(0)).isEqualByComparingTo("0");
        assertThat(SyncedPlayRecorder.hoursOf(-30)).isEqualByComparingTo("0");
    }

    @Test
    void mergeAddsTheDeltaAndCapsAtOneDay() {
        assertThat(SyncedPlayRecorder.merge(new BigDecimal("1.50"), new BigDecimal("0.75"))).isEqualByComparingTo("2.25");
        assertThat(SyncedPlayRecorder.merge(new BigDecimal("20"), new BigDecimal("6"))).isEqualByComparingTo("24");
        assertThat(SyncedPlayRecorder.merge(BigDecimal.ZERO, new BigDecimal("30"))).isEqualByComparingTo("24");
    }

    @Test
    void firstPlayOfTheDayCreatesTheEntryWithARecordOnlyAutoLine() {
        recorder.record(USER_ID, GAME_ID, RUN, DAY, 90);

        ArgumentCaptor<DiaryEntry> saved = ArgumentCaptor.forClass(DiaryEntry.class);
        verify(diaryEntryRepository).save(saved.capture());
        DiaryEntry entry = saved.getValue();
        assertThat(entry.getUserId()).isEqualTo(USER_ID);
        assertThat(entry.getEntryDate()).isEqualTo(DAY);
        assertThat(entry.getGames()).singleElement().satisfies(line -> {
            assertThat(line.getEntry()).isSameAs(entry);
            assertThat(line.getGameId()).isEqualTo(GAME_ID);
            assertThat(line.getExperienceId()).isEqualTo(RUN);
            assertThat(line.getHours()).isEqualByComparingTo("1.5");
            assertThat(line.isCountsHours()).isFalse();
            assertThat(line.isAutoSynced()).isTrue();
        });
    }

    @Test
    void laterSyncOfTheSameDayMergesIntoTheAutoLine() {
        DiaryEntry entry = existingDay(autoLine(GAME_ID, null, "1.50"));

        recorder.record(USER_ID, GAME_ID, null, DAY, 45);

        assertThat(entry.getGames()).singleElement().satisfies(line -> {
            assertThat(line.getHours()).isEqualByComparingTo("2.25");
            assertThat(line.isAutoSynced()).isTrue();
            assertThat(line.isCountsHours()).isFalse();
        });
        verify(diaryEntryRepository).save(entry);
    }

    @Test
    void mergeKeepsTheKnownRunWhenTheNewDeltaHasNone() {
        DiaryEntry entry = existingDay(autoLine(GAME_ID, RUN, "1"));

        recorder.record(USER_ID, GAME_ID, null, DAY, 60);

        assertThat(entry.getGames().getFirst().getExperienceId()).isEqualTo(RUN);
    }

    @Test
    void userLineOfTheSameGameIsNeverTouched() {
        DiaryEntryGame userLine = DiaryEntryGame.builder().gameId(GAME_ID).hours(new BigDecimal("2"))
                .note("Mine").countsHours(false).build();
        DiaryEntry entry = existingDay(userLine);

        recorder.record(USER_ID, GAME_ID, RUN, DAY, 120);

        assertThat(entry.getGames()).singleElement().satisfies(line -> {
            assertThat(line.getHours()).isEqualByComparingTo("2");
            assertThat(line.getExperienceId()).isNull();
            assertThat(line.isAutoSynced()).isFalse();
        });
        verify(diaryEntryRepository, never()).save(any());
    }

    @Test
    void otherGamesOfTheDayAndTheNoteAreKept() {
        DiaryEntryGame userLine = DiaryEntryGame.builder().gameId(OTHER_GAME_ID).hours(new BigDecimal("2"))
                .countsHours(true).build();
        DiaryEntry entry = existingDay(userLine);
        entry.setNote("Good day");

        recorder.record(USER_ID, GAME_ID, null, DAY, 30);

        assertThat(entry.getNote()).isEqualTo("Good day");
        assertThat(entry.getGames()).hasSize(2);
        assertThat(userLine.getHours()).isEqualByComparingTo("2");
        assertThat(userLine.isCountsHours()).isTrue();
    }

    @Test
    void zeroOrNegativeDeltaWritesNothing() {
        recorder.record(USER_ID, GAME_ID, RUN, DAY, 0);
        recorder.record(USER_ID, GAME_ID, RUN, DAY, -60);

        verify(diaryEntryRepository, never()).findByUserIdAndEntryDate(any(), any());
        verify(diaryEntryRepository, never()).save(any());
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

    private static DiaryEntryGame autoLine(Long gameId, Long experienceId, String hours) {
        return DiaryEntryGame.builder().gameId(gameId).experienceId(experienceId).hours(new BigDecimal(hours))
                .countsHours(false).autoSynced(true).build();
    }
}
