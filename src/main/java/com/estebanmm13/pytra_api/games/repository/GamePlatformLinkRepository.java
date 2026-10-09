package com.estebanmm13.pytra_api.games.repository;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GamePlatformLinkRepository extends JpaRepository<GamePlatformLink, Long> {
    Optional<GamePlatformLink> findByUserIdAndPlatformAndExternalId(Long userId, ExternalPlatform platform, String externalId);
    Optional<GamePlatformLink> findByUserIdAndGameId(Long userId, Long gameId);
}
