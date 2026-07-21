package com.estebanmm13.pytra_api.auth.repository;

import com.estebanmm13.pytra_api.auth.model.ExchangeCodeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExchangeCodeTokenRepository extends JpaRepository<ExchangeCodeToken, Long> {
    Optional<ExchangeCodeToken> findByTokenHash(String code);
}
