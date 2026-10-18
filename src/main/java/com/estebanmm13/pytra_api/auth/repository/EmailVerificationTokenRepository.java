package com.estebanmm13.pytra_api.auth.repository;

import com.estebanmm13.pytra_api.auth.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken,Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    Optional<EmailVerificationToken> findFirstByUser_IdOrderByExpiresAtDesc(Long userId);

    List<EmailVerificationToken> findAllByUser_IdAndConsumedAtIsNull(Long userId);

    /** Deletes the tokens that expired, or were consumed, before {@code cutoff}; returns how many. */
    @Transactional
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :cutoff OR t.consumedAt < :cutoff")
    int deleteExpiredOrConsumedBefore(@Param("cutoff") LocalDateTime cutoff);
}
