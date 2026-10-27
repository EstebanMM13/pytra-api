package com.estebanmm13.pytra_api.diary.repository;

import com.estebanmm13.pytra_api.diary.model.DiaryEntryGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DiaryEntryGameRepository extends JpaRepository<DiaryEntryGame, Long> {

    /** The days the user played one game, latest first (the entry is fetched for its date). */
    @Query("""
            SELECT g FROM DiaryEntryGame g JOIN FETCH g.entry e
            WHERE e.userId = :userId AND g.gameId = :gameId
            ORDER BY e.entryDate DESC
            """)
    List<DiaryEntryGame> findAllByUserIdAndGameIdLatestFirst(@Param("userId") Long userId, @Param("gameId") Long gameId);

    /**
     * Online diary hours per year: lines whose hours were added to the game's online playtime
     * ({@code countsHours} and no run). Lines added to a run are already in that run's hours, and lines of
     * synced games that never counted are left out, so the year stats never count an hour twice.
     */
    @Query("""
            SELECT new com.estebanmm13.pytra_api.diary.repository.OnlineDiaryYearHours(
                       extract(year from e.entryDate), SUM(g.hours))
            FROM DiaryEntryGame g JOIN g.entry e
            WHERE e.userId = :userId AND g.countsHours = true AND g.experienceId IS NULL
            GROUP BY extract(year from e.entryDate)
            """)
    List<OnlineDiaryYearHours> sumCountedOnlineHoursByYear(@Param("userId") Long userId);

    /** Same lines as {@link #sumCountedOnlineHoursByYear}, between two dates, per month and game. */
    @Query("""
            SELECT new com.estebanmm13.pytra_api.diary.repository.OnlineDiaryMonthHours(
                       extract(month from e.entryDate), g.gameId, SUM(g.hours), COUNT(g))
            FROM DiaryEntryGame g JOIN g.entry e
            WHERE e.userId = :userId AND g.countsHours = true AND g.experienceId IS NULL
              AND e.entryDate BETWEEN :from AND :to
            GROUP BY extract(month from e.entryDate), g.gameId
            """)
    List<OnlineDiaryMonthHours> sumCountedOnlineHoursByMonthAndGame(@Param("userId") Long userId,
                                                                    @Param("from") LocalDate from,
                                                                    @Param("to") LocalDate to);

    /**
     * Per-day totals of every diary line between two dates, oldest day first. All lines count, whatever
     * {@code countsHours} says: the diary is the record of when the user played, not of where the hours went.
     */
    @Query("""
            SELECT new com.estebanmm13.pytra_api.diary.repository.DiaryDayTotals(e.entryDate, SUM(g.hours), COUNT(g))
            FROM DiaryEntryGame g JOIN g.entry e
            WHERE e.userId = :userId AND e.entryDate BETWEEN :from AND :to
            GROUP BY e.entryDate
            ORDER BY e.entryDate
            """)
    List<DiaryDayTotals> sumHoursByDay(@Param("userId") Long userId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /** Every day with at least one game in the diary, oldest first (note-only days are not play days). */
    @Query("""
            SELECT DISTINCT e.entryDate
            FROM DiaryEntryGame g JOIN g.entry e
            WHERE e.userId = :userId
            ORDER BY e.entryDate
            """)
    List<LocalDate> findPlayedDates(@Param("userId") Long userId);

    /**
     * Before a run is deleted: its diary lines stop pointing at it and stop counting, since the run's hours
     * (diary ones included) go away with it. Nothing is ever subtracted twice.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("UPDATE DiaryEntryGame g SET g.experienceId = null, g.countsHours = false WHERE g.experienceId = :experienceId")
    int detachRun(@Param("experienceId") Long experienceId);

    /**
     * Before a run is merged into another one (its hours moved with it): its diary lines follow the hours
     * to the other run, keeping their {@code countsHours} flag, so nothing is added or subtracted twice.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("UPDATE DiaryEntryGame g SET g.experienceId = :targetId WHERE g.experienceId = :sourceId")
    int repointRun(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
}
