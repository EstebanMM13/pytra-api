package com.estebanmm13.pytra_api.games.repository;

import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import org.springframework.data.jpa.repository.EntityGraph;
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
    /** The game with its saga and genres in the same query: the detail response renders both. */
    @EntityGraph(attributePaths = {"saga", "genres"})
    Optional<Game> findWithSagaAndGenresByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
    long countByUserId(Long userId);
    long countByUserIdAndReviewStatus(Long userId, ReviewStatus reviewStatus);
    List<Game> findAllByUserIdAndReviewStatus(Long userId, ReviewStatus reviewStatus);

    @Query("SELECT g.id FROM Game g WHERE g.userId = :userId AND g.reviewStatus = :reviewStatus")
    List<Long> findIdsByUserIdAndReviewStatus(@Param("userId") Long userId, @Param("reviewStatus") ReviewStatus reviewStatus);

    /** Id, name and cover of every game of the user, as a projection (no entity loading). */
    @Query("SELECT new com.estebanmm13.pytra_api.games.repository.GameLabel(g.id, g.name, g.coverImageUrl) "
            + "FROM Game g WHERE g.userId = :userId")
    List<GameLabel> findLabelsByUserId(@Param("userId") Long userId);

    /** Every game of the user with its saga, ordered by game id. */
    @Query("SELECT new com.estebanmm13.pytra_api.games.repository.GameSagaRow(g.id, s.id, s.name) "
            + "FROM Game g LEFT JOIN g.saga s WHERE g.userId = :userId ORDER BY g.id")
    List<GameSagaRow> findSagaRowsByUserId(@Param("userId") Long userId);

    /** Every (game, genre) pair of the user, ordered by game id then genre id. */
    @Query("SELECT new com.estebanmm13.pytra_api.games.repository.GameGenreRow(g.id, ge.id, ge.name) "
            + "FROM Game g JOIN g.genres ge WHERE g.userId = :userId ORDER BY g.id, ge.id")
    List<GameGenreRow> findGenreRowsByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT g FROM Game g LEFT JOIN FETCH g.saga LEFT JOIN FETCH g.genres WHERE g.userId = :userId ORDER BY g.id")
    List<Game> findAllByUserIdWithSagaAndGenres(@Param("userId") Long userId);
}
