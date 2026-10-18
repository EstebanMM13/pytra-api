package com.estebanmm13.pytra_api.games.repository;

import com.estebanmm13.pytra_api.games.model.Saga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SagaRepository extends JpaRepository<Saga, Long>{
    List<Saga> findAllByUserId(Long userId);
    Optional<Saga> findByIdAndUserId(Long id, Long userId);
    /* LOWER() on both sides, exactly like the unique index ux_sagas_user_id_name_lower (see GameRepository). */
    @Query("SELECT COUNT(s) > 0 FROM Saga s WHERE s.userId = :userId AND LOWER(s.name) = LOWER(:name)")
    boolean existsByUserIdAndNameIgnoreCase(@Param("userId") Long userId, @Param("name") String name);
    @Query("SELECT COUNT(s) > 0 FROM Saga s WHERE s.userId = :userId AND LOWER(s.name) = LOWER(:name) AND s.id <> :id")
    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(@Param("userId") Long userId, @Param("name") String name,
                                                    @Param("id") Long id);
    long countByUserId(Long userId);
}
