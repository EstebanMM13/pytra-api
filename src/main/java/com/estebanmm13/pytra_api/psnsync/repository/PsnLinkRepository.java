package com.estebanmm13.pytra_api.psnsync.repository;

import com.estebanmm13.pytra_api.psnsync.model.PsnLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PsnLinkRepository extends JpaRepository<PsnLink, Long> {
    Optional<PsnLink> findByUserId(Long userId);

    /** Scheduled sync order: never-synced first, then least recently synced, so a run cut short never starves the same users. */
    @Query("SELECT l FROM PsnLink l ORDER BY l.lastSyncAt ASC NULLS FIRST, l.id ASC")
    List<PsnLink> findAllInScheduledSyncOrder();

    /**
     * Fresh DB check, never answered from a managed (possibly stale) PsnLink: with open-in-view the
     * request's EntityManager may hold the instance loaded before the PSN call.
     */
    @Query("SELECT COUNT(l) > 0 FROM PsnLink l WHERE l.userId = :userId AND l.accountId = :accountId")
    boolean isLinkedTo(@Param("userId") Long userId, @Param("accountId") String accountId);

    /**
     * Touches only last_sync_at, and only if the user is still linked to {@code accountId} (same
     * reasoning as SteamLinkRepository#markSynced).
     *
     * @return rows updated; 0 means the link was removed or switched to another account
     */
    @Modifying
    @Query("UPDATE PsnLink l SET l.lastSyncAt = :now WHERE l.userId = :userId AND l.accountId = :accountId")
    int markSynced(@Param("userId") Long userId, @Param("accountId") String accountId, @Param("now") LocalDateTime now);
}
