package com.estebanmm13.pytra_api.games.repository;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GamePlatformLinkRepository extends JpaRepository<GamePlatformLink, Long> {
    Optional<GamePlatformLink> findByUserIdAndPlatformAndExternalId(Long userId, ExternalPlatform platform, String externalId);
    Optional<GamePlatformLink> findByUserIdAndGameId(Long userId, Long gameId);
    Optional<GamePlatformLink> findByUserIdAndGameIdAndPlatform(Long userId, Long gameId, ExternalPlatform platform);
    long countByUserIdAndPlatform(Long userId, ExternalPlatform platform);

    /** Every link of the user on one platform with its game, in a single query (no N+1 during sync). */
    @Query("SELECT l FROM GamePlatformLink l JOIN FETCH l.game WHERE l.userId = :userId AND l.platform = :platform")
    List<GamePlatformLink> findAllByUserIdAndPlatformWithGame(@Param("userId") Long userId,
                                                              @Param("platform") ExternalPlatform platform);
}
