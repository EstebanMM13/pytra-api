package com.estebanmm13.pytra_api.platforms.repository;

import com.estebanmm13.pytra_api.platforms.model.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformRepository extends JpaRepository<Platform, Long> {
    List<Platform> findAllByOrderBySortOrderAscNameAscIdAsc();
    Optional<Platform> findByNameIgnoreCase(String name);
    Optional<Platform> findByLegacyCodeIgnoreCase(String legacyCode);
}
