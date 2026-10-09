package com.estebanmm13.pytra_api.steamsync.repository;

import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SteamLinkRepository extends JpaRepository<SteamLink, Long> {
    Optional<SteamLink> findByUserId(Long userId);
    Optional<SteamLink> findBySteamId(String steamId);
}
