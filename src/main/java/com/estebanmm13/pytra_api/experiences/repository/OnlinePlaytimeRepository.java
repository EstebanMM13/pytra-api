package com.estebanmm13.pytra_api.experiences.repository;

import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OnlinePlaytimeRepository extends JpaRepository<OnlinePlaytime, Long> {
    Optional<OnlinePlaytime> findByGameIdAndUserId(Long gameId, Long userId);
    List<OnlinePlaytime> findAllByUserIdOrderByTotalHoursDesc(Long userId);

    @Query("SELECT COALESCE(SUM(o.totalHours), 0.0) FROM OnlinePlaytime o WHERE o.userId = :userId")
    Double sumTotalHoursByUserId(@Param("userId") Long userId);
}
