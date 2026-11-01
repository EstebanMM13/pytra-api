package com.estebanmm13.pytra_api.achievements.repository;

import com.estebanmm13.pytra_api.achievements.model.UnlockedAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UnlockedAchievementRepository extends JpaRepository<UnlockedAchievement, Long> {

    List<UnlockedAchievement> findAllByUserIdOrderByUnlockedAtAscAchievementIdAsc(Long userId);

    /**
     * Stores the achievement unless the user already has it, so a concurrent record of the same id
     * (two tabs loading at once) keeps the first date instead of failing on the unique constraint.
     */
    @Modifying
    @Query(value = """
            INSERT INTO unlocked_achievements (user_id, achievement_id, unlocked_at)
            VALUES (:userId, :achievementId, :unlockedAt)
            ON CONFLICT (user_id, achievement_id) DO NOTHING""", nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId,
                        @Param("achievementId") String achievementId,
                        @Param("unlockedAt") LocalDateTime unlockedAt);
}
