package com.estebanmm13.pytra_api.psnsync.repository;

import com.estebanmm13.pytra_api.psnsync.model.PsnIgnoredTitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PsnIgnoredTitleRepository extends JpaRepository<PsnIgnoredTitle, Long> {

    List<PsnIgnoredTitle> findAllByUserIdOrderByNameAsc(Long userId);

    Optional<PsnIgnoredTitle> findByUserIdAndTitleId(Long userId, String titleId);

    boolean existsByUserIdAndTitleId(Long userId, String titleId);

    long countByUserId(Long userId);

    @Query("SELECT i.titleId FROM PsnIgnoredTitle i WHERE i.userId = :userId")
    Set<String> findTitleIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM PsnIgnoredTitle i WHERE i.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
