package com.estebanmm13.pytra_api.diary.service;

import com.estebanmm13.pytra_api.diary.model.DiaryEntry;
import com.estebanmm13.pytra_api.diary.model.DiaryEntryGame;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Writes the play a Steam/PSN sync observed into the user's diary. Must run inside the caller's
 * transaction (the sync's).
 *
 * <p>The lines it writes are "auto" lines ({@link DiaryEntryGame#isAutoSynced()}) and RECORD ONLY
 * ({@code countsHours = false}): the sync already added the hours to the run or online playtime, so totals,
 * year stats and online diary hours never see them twice. Only the activity heatmap (every diary line)
 * shows them.
 *
 * <p>One auto line per game and day: later syncs of the same day add their delta to it (Steam and PSN
 * linked to the same game share it). A line the user wrote for that game and day is never touched; the
 * observed play is then not recorded (a day holds one line per game).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncedPlayRecorder {

    /** A diary line holds at most one day of play (DB check {@code hours <= 24}). */
    static final BigDecimal MAX_LINE_HOURS = new BigDecimal("24");

    private final DiaryEntryRepository diaryEntryRepository;

    /**
     * Records {@code minutes} of play of {@code gameId} on {@code date}.
     *
     * @param experienceId the run the sync added the hours to; null when they went to the online playtime
     */
    public void record(Long userId, Long gameId, Long experienceId, LocalDate date, long minutes) {
        BigDecimal delta = hoursOf(minutes);
        if (delta.signum() <= 0) {
            return;
        }

        DiaryEntry entry = diaryEntryRepository.findByUserIdAndEntryDate(userId, date)
                .orElseGet(() -> DiaryEntry.builder().userId(userId).entryDate(date).build());
        DiaryEntryGame line = entry.getGames().stream()
                .filter(existing -> gameId.equals(existing.getGameId()))
                .findFirst()
                .orElse(null);

        if (line == null) {
            entry.getGames().add(DiaryEntryGame.builder()
                    .entry(entry)
                    .gameId(gameId)
                    .experienceId(experienceId)
                    .hours(merge(BigDecimal.ZERO, delta))
                    .countsHours(false)
                    .autoSynced(true)
                    .build());
        } else if (line.isAutoSynced()) {
            line.setHours(merge(line.getHours(), delta));
            if (experienceId != null) {
                line.setExperienceId(experienceId);
            }
        } else {
            log.debug("Diary {} already has a user line for game {}; synced play not recorded", date, gameId);
            return;
        }
        diaryEntryRepository.save(entry);
    }

    /** Minutes as diary hours (2 decimals, half up); zero for a non-positive delta. */
    static BigDecimal hoursOf(long minutes) {
        if (minutes <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /** The line's hours after adding {@code delta}, capped at one day. */
    static BigDecimal merge(BigDecimal current, BigDecimal delta) {
        return current.add(delta).min(MAX_LINE_HOURS);
    }
}
