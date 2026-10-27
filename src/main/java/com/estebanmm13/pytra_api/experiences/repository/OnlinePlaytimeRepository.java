package com.estebanmm13.pytra_api.experiences.repository;

import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OnlinePlaytimeRepository extends JpaRepository<OnlinePlaytime, Long> {
    @EntityGraph(attributePaths = "platforms")
    Optional<OnlinePlaytime> findByGameIdAndUserId(Long gameId, Long userId);
    List<OnlinePlaytime> findAllByUserIdOrderByTotalHoursDesc(Long userId);
    /** With platforms in the same query: the library merges them into each game's platform list. */
    @EntityGraph(attributePaths = "platforms")
    List<OnlinePlaytime> findAllByUserId(Long userId);
    List<OnlinePlaytime> findAllByUserIdAndGeneralRatingIsNotNull(Long userId);

    /** Online aggregates of the stats summary in one round trip; the rating sum is null when none is rated. */
    @Query("""
            SELECT COALESCE(SUM(o.totalHours), 0.0) AS hoursSum,
                   SUM(o.generalRating) AS ratingSum,
                   COUNT(o.generalRating) AS ratedCount,
                   COALESCE(SUM(CASE WHEN o.platinum = true THEN 1 ELSE 0 END), 0) AS platinumCount,
                   COALESCE(SUM(CASE WHEN o.fullCompletion = true THEN 1 ELSE 0 END), 0) AS fullCompletionCount
            FROM OnlinePlaytime o
            WHERE o.userId = :userId
            """)
    OnlinePlaytimeTotals totalsByUserId(@Param("userId") Long userId);

    @Query("SELECT new com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRow(o.gameId, o.totalHours, o.generalRating) "
            + "FROM OnlinePlaytime o WHERE o.userId = :userId")
    List<OnlinePlaytimeRow> findRowsByUserId(@Param("userId") Long userId);

    @Query("SELECT o.gameId, COALESCE(SUM(o.totalHours), 0.0) FROM OnlinePlaytime o WHERE o.userId = :userId GROUP BY o.gameId")
    List<Object[]> sumTotalHoursGroupedByGameId(@Param("userId") Long userId);

}
