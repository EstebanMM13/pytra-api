package com.estebanmm13.pytra_api.experiences.repository;

import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
    long countByUserIdAndGeneralRatingIsNotNull(Long userId);

    /** Exact sum (NUMERIC) of the online ratings; null when none is rated. */
    @Query("SELECT SUM(o.generalRating) FROM OnlinePlaytime o WHERE o.userId = :userId AND o.generalRating IS NOT NULL")
    BigDecimal sumGeneralRatingByUserId(@Param("userId") Long userId);

    @Query("SELECT o.gameId, COALESCE(SUM(o.totalHours), 0.0) FROM OnlinePlaytime o WHERE o.userId = :userId GROUP BY o.gameId")
    List<Object[]> sumTotalHoursGroupedByGameId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(o.totalHours), 0.0) FROM OnlinePlaytime o WHERE o.userId = :userId")
    Double sumTotalHoursByUserId(@Param("userId") Long userId);
}
