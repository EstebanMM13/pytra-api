package com.estebanmm13.pytra_api.auth.repository;

import com.estebanmm13.pytra_api.auth.model.ExchangeCodeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ExchangeCodeTokenRepository extends JpaRepository<ExchangeCodeToken, Long> {
    Optional<ExchangeCodeToken> findByTokenHash(String code);

    /**
     * Consumes the code only if nobody did yet; returns 1 for the single winner and 0 otherwise. Concurrent
     * callers serialize on the row lock, and the loser re-evaluates {@code consumedAt IS NULL} after the commit.
     */
    @Transactional
    @Modifying
    @Query("UPDATE ExchangeCodeToken t SET t.consumedAt = :now WHERE t.id = :id AND t.consumedAt IS NULL")
    int markConsumed(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Deletes the tokens that expired, or were consumed, before {@code cutoff}; returns how many. */
    @Transactional
    @Modifying
    @Query("DELETE FROM ExchangeCodeToken t WHERE t.expiresAt < :cutoff OR t.consumedAt < :cutoff")
    int deleteExpiredOrConsumedBefore(@Param("cutoff") LocalDateTime cutoff);
}
