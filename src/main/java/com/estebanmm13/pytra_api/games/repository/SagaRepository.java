package com.estebanmm13.pytra_api.games.repository;

import com.estebanmm13.pytra_api.games.model.Saga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SagaRepository extends JpaRepository<Saga, Long>{
    List<Saga> findAllByUserId(Long userId);
    Optional<Saga> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
    long countByUserId(Long userId);
}
