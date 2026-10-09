package com.estebanmm13.pytra_api.steamsync.repository;

import com.estebanmm13.pytra_api.steamsync.model.SteamIgnoredApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface SteamIgnoredAppRepository extends JpaRepository<SteamIgnoredApp, Long> {

    List<SteamIgnoredApp> findAllByUserIdOrderByNameAsc(Long userId);

    Optional<SteamIgnoredApp> findByUserIdAndAppId(Long userId, String appId);

    boolean existsByUserIdAndAppId(Long userId, String appId);

    @Query("SELECT i.appId FROM SteamIgnoredApp i WHERE i.userId = :userId")
    Set<String> findAppIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM SteamIgnoredApp i WHERE i.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
