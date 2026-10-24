package com.estebanmm13.pytra_api.diary.repository;

import com.estebanmm13.pytra_api.diary.model.DiaryEntryGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Before a run is deleted: its diary lines stop pointing at it and stop counting, since the run's hours
     * (diary ones included) go away with it. Nothing is ever subtracted twice.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("UPDATE DiaryEntryGame g SET g.experienceId = null, g.countsHours = false WHERE g.experienceId = :experienceId")
    int detachRun(@Param("experienceId") Long experienceId);
}
