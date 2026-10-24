package com.estebanmm13.pytra_api.diary.repository;

import com.estebanmm13.pytra_api.diary.model.DiaryEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, Long> {

    @EntityGraph(attributePaths = "games")
    Optional<DiaryEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);

    @EntityGraph(attributePaths = "games")
    List<DiaryEntry> findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(Long userId, LocalDate from, LocalDate to);
}
