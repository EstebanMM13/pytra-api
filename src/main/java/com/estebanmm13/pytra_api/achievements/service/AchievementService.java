package com.estebanmm13.pytra_api.achievements.service;

import com.estebanmm13.pytra_api.achievements.dto.UnlockedAchievementDto;
import com.estebanmm13.pytra_api.achievements.model.UnlockedAchievement;
import com.estebanmm13.pytra_api.achievements.repository.UnlockedAchievementRepository;
import com.estebanmm13.pytra_api.error.ResourceLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Achievements the user unlocked, so they stay unlocked even when the data that earned them changes.
 * The web computes them and records the new ones; recording an id again keeps its first date.
 */
@Service
public class AchievementService {

    /** Far above the web's catalog (~50): only guards the table against a client sending junk ids. */
    static final int MAX_STORED_PER_USER = 300;
    public static final String ACHIEVEMENT_LIMIT = "ACHIEVEMENT_LIMIT";

    private final UnlockedAchievementRepository repository;
    private final Clock clock;

    @Autowired
    public AchievementService(UnlockedAchievementRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    AchievementService(UnlockedAchievementRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** The user's unlocked achievements, oldest unlock first. */
    @Transactional(readOnly = true)
    public List<UnlockedAchievementDto> findUnlocked(Long userId) {
        return toDtos(repository.findAllByUserIdOrderByUnlockedAtAscAchievementIdAsc(userId));
    }

    /** Stores the ids the user does not have yet (dated now) and answers the whole updated list. */
    @Transactional
    public List<UnlockedAchievementDto> record(Long userId, List<String> ids) {
        List<UnlockedAchievement> stored = repository.findAllByUserIdOrderByUnlockedAtAscAchievementIdAsc(userId);
        Set<String> storedIds = stored.stream().map(UnlockedAchievement::getAchievementId).collect(Collectors.toSet());
        Set<String> newIds = new LinkedHashSet<>(ids);
        newIds.removeAll(storedIds);
        if (newIds.isEmpty()) {
            return toDtos(stored);
        }
        if (storedIds.size() + newIds.size() > MAX_STORED_PER_USER) {
            throw new ResourceLimitExceededException(ACHIEVEMENT_LIMIT);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        newIds.forEach(id -> repository.insertIfAbsent(userId, id, now));
        return toDtos(repository.findAllByUserIdOrderByUnlockedAtAscAchievementIdAsc(userId));
    }

    private static List<UnlockedAchievementDto> toDtos(List<UnlockedAchievement> list) {
        return list.stream()
                .map(a -> new UnlockedAchievementDto(a.getAchievementId(), a.getUnlockedAt()))
                .toList();
    }
}
