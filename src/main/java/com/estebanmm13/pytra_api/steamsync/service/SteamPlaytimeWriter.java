package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.model.Platform;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Writes Steam hours into the user's data. Must run inside the caller's transaction.
 *
 * <p>Steam hours are never spread over several Experiences: Steam only reports one cumulative
 * total per game, so there is no way to know which run an hour belongs to. Instead:
 * <ul>
 *   <li>SINGLEPLAYER: the link keeps ONE "canonical" Experience up to date
 *       (GamePlatformLink.experienceId), separate from any run the user created by hand.</li>
 *   <li>ONLINE/HYBRID: hours go to OnlinePlaytime, which is already a single total per game.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamPlaytimeWriter {

    static final String STEAM_IMPORT_NOTE = "Horas importadas automáticamente desde Steam";
    static final String STEAM_RUN_LABEL = "Importado de Steam";

    private final ExperienceRepository experienceRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;

    /**
     * Confirm-time import of the whole Steam total.
     *
     * <p>ONLINE/HYBRID rule: the user may already have typed an OnlinePlaytime by hand for this
     * game, and that number most likely already includes (part of) the Steam hours. Adding the
     * Steam total on top would double count, so the total becomes max(existing, steamTotal).
     * From then on only positive deltas reported by later syncs are added.
     */
    public void importTotalOnConfirm(GamePlatformLink link, Game game) {
        double steamTotalHours = link.getLastSyncedPlaytimeMinutes() / 60.0;

        if (game.getCategory() == GameCategory.SINGLEPLAYER) {
            if (findCanonicalExperience(link).isEmpty()) {
                createCanonicalExperience(link, game.getId(), steamTotalHours);
            }
            return;
        }

        OnlinePlaytime onlinePlaytime = findOrNewOnlinePlaytime(link.getUserId(), game.getId());
        onlinePlaytime.setTotalHours(Math.max(onlinePlaytime.getTotalHours(), steamTotalHours));
        onlinePlaytime.setLastSessionAt(link.getLastSyncedAt());
        onlinePlaytimeRepository.save(onlinePlaytime);
    }

    /** Adds hours played since the previous sync to a confirmed game. */
    public void addDelta(GamePlatformLink link, Game game, double hoursToAdd, LocalDateTime sessionAt) {
        GameCategory category = game.getCategory();
        if (category == null) {
            log.debug("GamePlatformLink {} has no category yet; delta not applied", link.getId());
            return;
        }

        if (category == GameCategory.SINGLEPLAYER) {
            Optional<Experience> canonical = findCanonicalExperience(link);
            if (canonical.isPresent()) {
                Experience experience = canonical.get();
                experience.setHours(experience.getHours() + hoursToAdd);
                experienceRepository.save(experience);
            } else {
                // Game confirmed outside the Steam flow, linked by name, or its canonical run was
                // deleted: start a new canonical run holding only the hours tracked from now on.
                createCanonicalExperience(link, game.getId(), hoursToAdd);
            }
            return;
        }

        OnlinePlaytime onlinePlaytime = findOrNewOnlinePlaytime(link.getUserId(), game.getId());
        onlinePlaytime.setTotalHours(onlinePlaytime.getTotalHours() + hoursToAdd);
        onlinePlaytime.setLastSessionAt(sessionAt);
        onlinePlaytimeRepository.save(onlinePlaytime);
    }

    private Optional<Experience> findCanonicalExperience(GamePlatformLink link) {
        if (link.getExperienceId() == null) return Optional.empty();
        return experienceRepository.findByIdAndUserId(link.getExperienceId(), link.getUserId());
    }

    private void createCanonicalExperience(GamePlatformLink link, Long gameId, double hours) {
        Experience experience = experienceRepository.save(Experience.builder()
                .userId(link.getUserId())
                .gameId(gameId)
                .runLabel(STEAM_RUN_LABEL)
                .status(ExperienceStatus.EN_CURSO)
                .hours(hours)
                .platform(Platform.PC)
                .platinum(false)
                .replay(false)
                .notes(STEAM_IMPORT_NOTE)
                .build());
        link.setExperienceId(experience.getId());
    }

    private OnlinePlaytime findOrNewOnlinePlaytime(Long userId, Long gameId) {
        return onlinePlaytimeRepository.findByGameIdAndUserId(gameId, userId)
                .orElseGet(() -> OnlinePlaytime.builder().userId(userId).gameId(gameId).totalHours(0.0).build());
    }
}
