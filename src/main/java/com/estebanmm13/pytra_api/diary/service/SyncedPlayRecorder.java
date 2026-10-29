package com.estebanmm13.pytra_api.diary.service;

import com.estebanmm13.pytra_api.diary.model.DiaryEntry;
import com.estebanmm13.pytra_api.diary.model.DiaryEntryGame;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Writes the play a Steam/PSN sync observed into the user's diary.
 *
 * <p>The diary is secondary to the sync and must never break it: {@link #record} called inside the sync's
 * transaction only schedules the write, which runs AFTER that transaction commits, in a new transaction of
 * its own. A failure there (e.g. the user saving the same day at that moment, or a Steam and a PSN sync
 * racing for the same day's entry) is retried once, then logged and dropped; the sync's hours and baselines
 * are already committed. The day's entry is locked while its auto line is merged, so concurrent syncs never
 * lose each other's delta.
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
public class SyncedPlayRecorder {

    /** A diary line holds at most one day of play (DB check {@code hours <= 24}). */
    static final BigDecimal MAX_LINE_HOURS = new BigDecimal("24");
    private static final int ATTEMPTS = 2;

    private final DiaryEntryRepository diaryEntryRepository;
    private final TransactionTemplate newTransaction;

    public SyncedPlayRecorder(DiaryEntryRepository diaryEntryRepository, PlatformTransactionManager transactionManager) {
        this.diaryEntryRepository = diaryEntryRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Records {@code minutes} of play of {@code gameId} on {@code date}: after the current transaction
     * commits (nothing is written if it rolls back), or at once when there is none. Never throws.
     *
     * @param experienceId the run the sync added the hours to; null when they went to the online playtime
     */
    public void record(Long userId, Long gameId, Long experienceId, LocalDate date, long minutes) {
        BigDecimal delta = hoursOf(minutes);
        if (delta.signum() <= 0) {
            return;
        }
        Runnable write = () -> writeSafely(userId, gameId, experienceId, date, delta);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write.run();
                }
            });
        } else {
            write.run();
        }
    }

    private void writeSafely(Long userId, Long gameId, Long experienceId, LocalDate date, BigDecimal delta) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                // REQUIRES_NEW: inside afterCommit the sync's (finished) transaction is still bound.
                newTransaction.executeWithoutResult(status -> write(userId, gameId, experienceId, date, delta));
                return;
            } catch (DataIntegrityViolationException e) {
                // Lost the race to create the day's entry or line: the retry finds it and merges.
                if (attempt == ATTEMPTS) {
                    logDropped(userId, gameId, date, e);
                }
            } catch (RuntimeException e) {
                logDropped(userId, gameId, date, e);
                return;
            }
        }
    }

    private static void logDropped(Long userId, Long gameId, LocalDate date, RuntimeException e) {
        // Class name only: driver messages can echo the offending values.
        log.warn("Diary record of synced play for userId {} gameId {} on {} dropped: {}",
                userId, gameId, date, e.getClass().getSimpleName());
    }

    /** One attempt, inside a transaction. */
    void write(Long userId, Long gameId, Long experienceId, LocalDate date, BigDecimal delta) {
        DiaryEntry entry = diaryEntryRepository.findForUpdate(userId, date)
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
        // Flush inside the attempt so a constraint violation surfaces here and is retried.
        diaryEntryRepository.saveAndFlush(entry);
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
