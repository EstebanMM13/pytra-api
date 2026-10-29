package com.estebanmm13.pytra_api.diary.repository;

import com.estebanmm13.pytra_api.diary.model.DiaryEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, Long> {

    @EntityGraph(attributePaths = "games")
    Optional<DiaryEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);

    /** The day's entry, row-locked until the transaction ends (its games load lazily). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM DiaryEntry e WHERE e.userId = :userId AND e.entryDate = :entryDate")
    Optional<DiaryEntry> findForUpdate(@Param("userId") Long userId, @Param("entryDate") LocalDate entryDate);

    @EntityGraph(attributePaths = "games")
    List<DiaryEntry> findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(Long userId, LocalDate from, LocalDate to);
}
