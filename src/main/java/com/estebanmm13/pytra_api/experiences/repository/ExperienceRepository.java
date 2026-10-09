package com.estebanmm13.pytra_api.experiences.repository;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExperienceRepository extends JpaRepository<Experience, Long> {
    List<Experience> findAllByGameIdAndUserId(Long gameId, Long userId);
    Optional<Experience> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
    long countByUserIdAndPlatinumTrue(Long userId);
    List<Experience> findAllByUserIdAndRatingIsNotNullOrderByRatingDesc(Long userId);

    @Query("SELECT COALESCE(SUM(e.hours), 0.0) FROM Experience e WHERE e.userId = :userId")
    Double sumHoursByUserId(@Param("userId") Long userId);

    @Query("SELECT e.gameId, COALESCE(SUM(e.hours), 0.0) FROM Experience e WHERE e.userId = :userId GROUP BY e.gameId")
    List<Object[]> sumHoursGroupedByGameId(@Param("userId") Long userId);

    @Query("SELECT e.year, COALESCE(SUM(e.hours), 0.0), COUNT(e) FROM Experience e WHERE e.userId = :userId AND e.year IS NOT NULL GROUP BY e.year ORDER BY e.year DESC")
    List<Object[]> statsByYear(@Param("userId") Long userId);
}
