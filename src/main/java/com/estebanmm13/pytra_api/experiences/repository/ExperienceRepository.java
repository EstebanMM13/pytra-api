package com.estebanmm13.pytra_api.experiences.repository;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExperienceRepository extends JpaRepository<Experience, Long> {
    // Lists fetch the (eager) platform in the same query instead of one extra select per distinct platform.
    @EntityGraph(attributePaths = "platform")
    List<Experience> findAllByGameIdAndUserId(Long gameId, Long userId);
    @EntityGraph(attributePaths = "platform")
    Optional<Experience> findByIdAndUserId(Long id, Long userId);
    /**
     * Every all-time aggregate of the stats summary in one round trip. SUM(CASE ...) of an empty set is null,
     * hence the COALESCE; the rating sum stays null when no run is rated (same as a plain SUM).
     */
    @Query("""
            SELECT COUNT(e) AS experienceCount,
                   COALESCE(SUM(e.hours), 0.0) AS hoursSum,
                   COALESCE(SUM(CASE WHEN e.platinum = true THEN 1 ELSE 0 END), 0) AS platinumCount,
                   COALESCE(SUM(CASE WHEN e.fullCompletion = true THEN 1 ELSE 0 END), 0) AS fullCompletionCount,
                   COALESCE(SUM(CASE WHEN e.replay = true THEN 1 ELSE 0 END), 0) AS replayCount,
                   COALESCE(SUM(CASE WHEN e.status = com.estebanmm13.pytra_api.experiences.model.ExperienceStatus.COMPLETADO
                                     THEN 1 ELSE 0 END), 0) AS completedCount,
                   COALESCE(SUM(CASE WHEN e.status = com.estebanmm13.pytra_api.experiences.model.ExperienceStatus.ABANDONADO
                                     THEN 1 ELSE 0 END), 0) AS abandonedCount,
                   COALESCE(SUM(CASE WHEN e.status = com.estebanmm13.pytra_api.experiences.model.ExperienceStatus.EN_CURSO
                                     THEN 1 ELSE 0 END), 0) AS inProgressCount,
                   SUM(e.rating) AS ratingSum,
                   COUNT(e.rating) AS ratedCount
            FROM Experience e
            WHERE e.userId = :userId
            """)
    ExperienceTotals totalsByUserId(@Param("userId") Long userId);
    @EntityGraph(attributePaths = "platform")
    List<Experience> findAllByUserId(Long userId);
    @EntityGraph(attributePaths = "platform")
    List<Experience> findAllByUserIdAndStatus(Long userId, ExperienceStatus status);
    @EntityGraph(attributePaths = "platform")
    List<Experience> findAllByUserIdAndRatingIsNotNullOrderByRatingDesc(Long userId);

    /** Hours and rating totals of the user's runs, one row per game. */
    @Query("""
            SELECT new com.estebanmm13.pytra_api.experiences.repository.GameRunTotals(
                       e.gameId, COALESCE(SUM(e.hours), 0.0), SUM(e.rating), COUNT(e.rating))
            FROM Experience e
            WHERE e.userId = :userId
            GROUP BY e.gameId
            """)
    List<GameRunTotals> totalsGroupedByGameId(@Param("userId") Long userId);

    @Query("SELECT e.gameId, COALESCE(SUM(e.hours), 0.0) FROM Experience e WHERE e.userId = :userId GROUP BY e.gameId")
    List<Object[]> sumHoursGroupedByGameId(@Param("userId") Long userId);
}
