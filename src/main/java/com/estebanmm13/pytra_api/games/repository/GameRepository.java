package com.estebanmm13.pytra_api.games.repository;

import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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
    /** With saga and genres in the same query: the pending queues render both. */
    @EntityGraph(attributePaths = {"saga", "genres"})
    List<Game> findAllByUserIdAndReviewStatus(Long userId, ReviewStatus reviewStatus);

    /** Pending games IGDB was never asked about, oldest first. */
    @Query("SELECT g.id FROM Game g WHERE g.userId = :userId AND g.reviewStatus = :reviewStatus "
            + "AND g.igdbLookupAt IS NULL ORDER BY g.id")
    List<Long> findIdsWithoutIgdbLookup(@Param("userId") Long userId, @Param("reviewStatus") ReviewStatus reviewStatus);

    /**
     * Renames a pending game only while it still has its imported, never edited name ({@code expected}).
     * A single conditional UPDATE, so a concurrent edit or confirm is never overwritten; 0 when the game
     * changed meanwhile. A name taken by another game of the user fails on the unique index.
     */
    @Modifying
    @Query("UPDATE Game g SET g.name = :newName, g.updatedAt = :now WHERE g.id = :id AND g.userId = :userId "
            + "AND g.reviewStatus = :reviewStatus AND g.name = :expected AND g.importedName = :expected")
    int renameImported(@Param("userId") Long userId, @Param("id") Long id, @Param("expected") String expected,
                       @Param("newName") String newName, @Param("reviewStatus") ReviewStatus reviewStatus,
                       @Param("now") LocalDateTime now);

    /** Bulk stamp (does not touch updated_at): IGDB was asked about these games. */
    @Modifying
    @Query("UPDATE Game g SET g.igdbLookupAt = :at WHERE g.userId = :userId AND g.id IN :ids")
    int markIgdbLookup(@Param("userId") Long userId, @Param("ids") Collection<Long> ids, @Param("at") LocalDateTime at);

    /**
     * The game, row-locked until the transaction ends (SELECT ... FOR UPDATE), so a background write
     * never interleaves with a concurrent edit of the same game.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Game g WHERE g.id = :id AND g.userId = :userId")
    Optional<Game> findForUpdateByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT g.id FROM Game g WHERE g.userId = :userId AND g.reviewStatus = :reviewStatus")
    List<Long> findIdsByUserIdAndReviewStatus(@Param("userId") Long userId, @Param("reviewStatus") ReviewStatus reviewStatus);

    /** Id, name and cover of every game of the user, as a projection (no entity loading). */
    @Query("SELECT new com.estebanmm13.pytra_api.games.repository.GameLabel(g.id, g.name, g.coverImageUrl) "
            + "FROM Game g WHERE g.userId = :userId")
    List<GameLabel> findLabelsByUserId(@Param("userId") Long userId);

    /** Every game of the user in that review status with its saga, ordered by game id. */
    @Query("SELECT new com.estebanmm13.pytra_api.games.repository.GameSagaRow(g.id, s.id, s.name) "
            + "FROM Game g LEFT JOIN g.saga s WHERE g.userId = :userId AND g.reviewStatus = :reviewStatus ORDER BY g.id")
    List<GameSagaRow> findSagaRowsByUserIdAndReviewStatus(@Param("userId") Long userId,
                                                          @Param("reviewStatus") ReviewStatus reviewStatus);

    /** Every (game, genre) pair of the user's games in that review status, ordered by game id then genre id. */
    @Query("SELECT new com.estebanmm13.pytra_api.games.repository.GameGenreRow(g.id, ge.id, ge.name) "
            + "FROM Game g JOIN g.genres ge WHERE g.userId = :userId AND g.reviewStatus = :reviewStatus "
            + "ORDER BY g.id, ge.id")
    List<GameGenreRow> findGenreRowsByUserIdAndReviewStatus(@Param("userId") Long userId,
                                                            @Param("reviewStatus") ReviewStatus reviewStatus);

    @Query("SELECT DISTINCT g FROM Game g LEFT JOIN FETCH g.saga LEFT JOIN FETCH g.genres WHERE g.userId = :userId ORDER BY g.id")
    List<Game> findAllByUserIdWithSagaAndGenres(@Param("userId") Long userId);
}
