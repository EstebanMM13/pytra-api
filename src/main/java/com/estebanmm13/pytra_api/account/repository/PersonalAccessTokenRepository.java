package com.estebanmm13.pytra_api.account.repository;

import com.estebanmm13.pytra_api.account.model.PersonalAccessToken;
import org.springframework.data.jpa.repository.EntityGraph;
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
public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {

    /** With its owner in the same query: authentication needs the username. */
    @EntityGraph(attributePaths = "user")
    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);

    List<PersonalAccessToken> findAllByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<PersonalAccessToken> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_Id(Long userId);

    /** Revokes every token of the user (password reset). Runs in the caller's transaction. */
    @Modifying
    @Query("DELETE FROM PersonalAccessToken t WHERE t.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    /**
     * Stamps the last use only when the stored one is older than {@code threshold} (or unset), so a client
     * polling every few seconds writes at most one row update per window. Own transaction: the caller
     * (an authentication filter) runs outside any.
     */
    @Transactional
    @Modifying
    @Query("UPDATE PersonalAccessToken t SET t.lastUsedAt = :now WHERE t.id = :id "
            + "AND (t.lastUsedAt IS NULL OR t.lastUsedAt < :threshold)")
    int touchLastUsedAt(@Param("id") Long id, @Param("now") LocalDateTime now,
                        @Param("threshold") LocalDateTime threshold);
}
