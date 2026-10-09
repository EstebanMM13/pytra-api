package com.estebanmm13.pytra_api.steamsync.repository;

import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SteamLinkRepository extends JpaRepository<SteamLink, Long> {
    Optional<SteamLink> findByUserId(Long userId);
    Optional<SteamLink> findBySteamId(String steamId);

    /**
     * Fresh DB check, never answered from a managed (possibly stale) SteamLink: with
     * open-in-view the request's EntityManager may hold the instance loaded before the Steam call.
     */
    @Query("SELECT COUNT(l) > 0 FROM SteamLink l WHERE l.userId = :userId AND l.steamId = :steamId")
    boolean isLinkedTo(@Param("userId") Long userId, @Param("steamId") String steamId);

    /**
     * Touches only last_sync_at, and only if the user is still linked to {@code steamId}. Writing
     * through the entity instead would flush every column of a stale instance and silently revert
     * a relink that happened meanwhile.
     *
     * @return rows updated; 0 means the link was removed or switched to another account
     */
    @Modifying
    @Query("UPDATE SteamLink l SET l.lastSyncAt = :now WHERE l.userId = :userId AND l.steamId = :steamId")
    int markSynced(@Param("userId") Long userId, @Param("steamId") String steamId, @Param("now") LocalDateTime now);
}
