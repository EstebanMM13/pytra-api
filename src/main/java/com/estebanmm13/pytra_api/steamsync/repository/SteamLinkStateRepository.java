package com.estebanmm13.pytra_api.steamsync.repository;

import com.estebanmm13.pytra_api.steamsync.model.SteamLinkState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SteamLinkStateRepository extends JpaRepository<SteamLinkState, Long> {
    Optional<SteamLinkState> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM SteamLinkState s WHERE s.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
