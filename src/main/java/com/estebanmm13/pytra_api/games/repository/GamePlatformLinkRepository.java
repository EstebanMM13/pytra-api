package com.estebanmm13.pytra_api.games.repository;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface GamePlatformLinkRepository extends JpaRepository<GamePlatformLink, Long> {
    Optional<GamePlatformLink> findByUserIdAndPlatformAndExternalId(Long userId, ExternalPlatform platform, String externalId);
    /** Every platform link of one game (Steam and the PSN generations can coexist). */
    List<GamePlatformLink> findAllByUserIdAndGameId(Long userId, Long gameId);
    Optional<GamePlatformLink> findByUserIdAndGameIdAndPlatform(Long userId, Long gameId, ExternalPlatform platform);
    long countByUserIdAndPlatform(Long userId, ExternalPlatform platform);

    /** Every link of the user on one platform with its game, in a single query (no N+1 during sync). */
    @Query("SELECT l FROM GamePlatformLink l JOIN FETCH l.game WHERE l.userId = :userId AND l.platform = :platform")
    List<GamePlatformLink> findAllByUserIdAndPlatformWithGame(@Param("userId") Long userId,
                                                              @Param("platform") ExternalPlatform platform);

    /** Same as {@link #findAllByUserIdAndPlatformWithGame} for several platforms (PSN spans PS4 and PS5). */
    @Query("SELECT l FROM GamePlatformLink l JOIN FETCH l.game WHERE l.userId = :userId AND l.platform IN :platforms")
    List<GamePlatformLink> findAllByUserIdAndPlatformInWithGame(@Param("userId") Long userId,
                                                                @Param("platforms") Collection<ExternalPlatform> platforms);

    long countByUserIdAndPlatformIn(Long userId, Collection<ExternalPlatform> platforms);

    /** Ids of the user's games that have a link on any of {@code platforms}. */
    @Query("SELECT DISTINCT l.game.id FROM GamePlatformLink l WHERE l.userId = :userId AND l.platform IN :platforms")
    Set<Long> findGameIdsByUserIdAndPlatformIn(@Param("userId") Long userId,
                                               @Param("platforms") Collection<ExternalPlatform> platforms);
}
