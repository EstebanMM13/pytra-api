package com.estebanmm13.pytra_api.experiences.service.gameStats;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod;
import com.estebanmm13.pytra_api.experiences.model.Ratings;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes {@link GameExperienceStats} with ONE query per call (all the user's experiences),
 * grouped in memory, so listing the library never runs a query per game.
 */
@Component
@RequiredArgsConstructor
public class GameExperienceStatsCalculator {

    private final ExperienceRepository experienceRepository;

    /** Stats keyed by game id; games without experiences are absent (use {@link GameExperienceStats#EMPTY}). */
    public Map<Long, GameExperienceStats> byGame(Long userId) {
        Map<Long, List<Experience>> grouped = new HashMap<>();
        for (Experience experience : experienceRepository.findAllByUserId(userId)) {
            grouped.computeIfAbsent(experience.getGameId(), id -> new ArrayList<>()).add(experience);
        }
        Map<Long, GameExperienceStats> result = new HashMap<>();
        grouped.forEach((gameId, experiences) -> result.put(gameId, of(experiences)));
        return result;
    }

    public GameExperienceStats forGame(Long gameId, Long userId) {
        return of(experienceRepository.findAllByGameIdAndUserId(gameId, userId));
    }

    public static GameExperienceStats of(List<Experience> experiences) {
        if (experiences.isEmpty()) {
            return GameExperienceStats.EMPTY;
        }
        double totalHours = 0;
        BigDecimal bestRating = null;
        Integer lastPlayedYear = null;
        boolean hasPlatinum = false;
        Experience latest = null;

        for (Experience experience : experiences) {
            totalHours += experience.getHours() != null ? experience.getHours() : 0.0;
            if (experience.getRating() != null && (bestRating == null || experience.getRating().compareTo(bestRating) > 0)) {
                bestRating = experience.getRating();
            }
            Integer year = ExperiencePeriod.yearOf(experience);
            if (year != null && (lastPlayedYear == null || year > lastPlayedYear)) {
                lastPlayedYear = year;
            }
            hasPlatinum |= Boolean.TRUE.equals(experience.getPlatinum());
            if (latest == null || ExperiencePeriod.RECENCY.compare(experience, latest) > 0) {
                latest = experience;
            }
        }
        return new GameExperienceStats(
                experiences.size(), totalHours, Ratings.normalize(bestRating), latest.getStatus(), lastPlayedYear, hasPlatinum);
    }
}
