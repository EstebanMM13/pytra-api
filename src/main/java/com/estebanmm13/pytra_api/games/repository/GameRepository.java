package com.estebanmm13.pytra_api.games.repository;

import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {
    List<Game> findAllByUserId(Long userId);
    Optional<Game> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
    long countByUserId(Long userId);
    List<Game> findAllByUserIdAndReviewStatus(Long userId, ReviewStatus reviewStatus);

    @Query("SELECT DISTINCT g FROM Game g LEFT JOIN FETCH g.saga LEFT JOIN FETCH g.genres WHERE g.userId = :userId")
    List<Game> findAllByUserIdWithSagaAndGenres(@Param("userId") Long userId);
}
