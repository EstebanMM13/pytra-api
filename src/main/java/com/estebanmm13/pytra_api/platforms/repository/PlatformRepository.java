package com.estebanmm13.pytra_api.platforms.repository;

import com.estebanmm13.pytra_api.platforms.model.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformRepository extends JpaRepository<Platform, Long> {
    List<Platform> findAllByOrderBySortOrderAscNameAscIdAsc();
    /* LOWER() on both sides, exactly like the unique index ux_platforms_name_lower (see GameRepository). */
    @Query("SELECT p FROM Platform p WHERE LOWER(p.name) = LOWER(:name)")
    Optional<Platform> findByNameIgnoreCase(@Param("name") String name);
    Optional<Platform> findByLegacyCodeIgnoreCase(String legacyCode);
}
