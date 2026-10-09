package com.estebanmm13.pytra_api.stats.repository;

import com.estebanmm13.pytra_api.stats.model.YearNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface YearNoteRepository extends JpaRepository<YearNote, Long> {
    Optional<YearNote> findByUserIdAndYear(Long userId, Integer year);
    List<YearNote> findAllByUserIdOrderByYearDesc(Long userId);
}
