package com.estebanmm13.pytra_api.steamsync.repository;

import com.estebanmm13.pytra_api.steamsync.model.SteamLinkState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SteamLinkStateRepository extends JpaRepository<SteamLinkState, Long> {
    Optional<SteamLinkState> findByTokenHash(String tokenHash);
}
