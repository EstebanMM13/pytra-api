package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.experiences.service.hybridPlaytime.HybridPlaytimeReconciler;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.platforms.service.PlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes hours reported by an external platform (Steam, PSN) into the user's data. Must run inside
 * the caller's transaction.
 *
 * <p>Synced hours are never spread over several Experiences: platforms only report one cumulative
 * total per title, so there is no way to know which run an hour belongs to. Instead:
 * <ul>
 *   <li>SINGLEPLAYER: each link keeps ONE "canonical" Experience up to date
 *       (GamePlatformLink.experienceId). When the game already has exactly one in-progress run on the
 *       link's platform, that run is adopted; otherwise a separate run is created, labelled, noted and
 *       placed on the catalog platform given by {@link ImportedRunSource}.</li>
 *   <li>ONLINE: hours go to OnlinePlaytime, which is already a single total per game.</li>
 *   <li>HYBRID: hours go to OnlinePlaytime too, minus the hours of the runs logged on the linked
 *       platforms (the platform total already includes them). See {@link HybridPlaytimeReconciler}.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalPlaytimeWriter {

    private final ExperienceRepository experienceRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;
    private final PlatformService platformService;
    private final HybridPlaytimeReconciler hybridPlaytimeReconciler;

    /**
     * Moves the link's baseline to the platform's current total. Managed entities are dirty-checked,
     * so a link whose playtime did not change is not written at all.
     *
     * @return true when hours were added to a confirmed game
     */
    public boolean applySyncedTotal(GamePlatformLink link, long currentMinutes, LocalDateTime now) {
        long baselineMinutes = link.getLastSyncedPlaytimeMinutes();
        if (currentMinutes == baselineMinutes) {
            return false;
        }

        link.setLastSyncedPlaytimeMinutes(currentMinutes);
        link.setLastSyncedAt(now);

        // Negative delta (refund, lost family-sharing access, platform-side reset): only move the
        // baseline down. Never subtract from the user's data, and never re-add the old hours later.
        if (currentMinutes < baselineMinutes) {
            return false;
        }

        // PENDING_REVIEW games only track the baseline: confirm imports the full total at once.
        Game game = link.getGame();
        if (game.getReviewStatus() != ReviewStatus.CONFIRMED) {
            return false;
        }
        if (game.getCategory() == GameCategory.HYBRID) {
            return hybridPlaytimeReconciler.applySyncedChange(link, baselineMinutes, now);
        }
        return addDelta(link, game, (currentMinutes - baselineMinutes) / 60.0, now);
    }

    /**
     * Confirm-time import of the whole synced total of every link of the game (one game can be
     * linked to Steam and to the PS4 and PS5 versions on PSN at the same time).
     *
     * <p>SINGLEPLAYER: each link gets its canonical Experience with its own total, on its own platform. An
     * adopted in-progress run keeps max(its hours, the synced total): its hours most likely already
     * include (part of) the synced ones, like the ONLINE rule below.
     *
     * <p>ONLINE/HYBRID rule: the user may already have typed an OnlinePlaytime by hand for this game,
     * and that number most likely already includes (part of) the synced hours. Adding the synced total
     * on top would double count, so the total becomes max(existing, synced hours). From then on only
     * changes reported by later syncs are applied. For ONLINE the synced hours are the sum of the links'
     * totals; for HYBRID they are the synced share, without the runs already logged on the linked
     * platforms ({@link HybridPlaytimeReconciler}).
     */
    public void importTotalsOnConfirm(List<GamePlatformLink> links, Game game) {
        if (links.isEmpty()) {
            return;
        }

        if (game.getCategory() == GameCategory.SINGLEPLAYER) {
            for (GamePlatformLink link : links) {
                if (findCanonicalExperience(link).isPresent()) {
                    continue;
                }
                double syncedHours = link.getLastSyncedPlaytimeMinutes() / 60.0;
                Optional<Experience> inProgress = findAdoptableRun(link, game.getId());
                if (inProgress.isPresent()) {
                    Experience run = inProgress.get();
                    run.setHours(Math.max(run.getHours(), syncedHours));
                    experienceRepository.save(run);
                    link.setExperienceId(run.getId());
                } else {
                    createCanonicalExperience(link, game.getId(), syncedHours);
                }
            }
            return;
        }

        double syncedTotalHours = game.getCategory() == GameCategory.HYBRID
                ? hybridPlaytimeReconciler.shareOf(game, links)
                : links.stream().mapToLong(GamePlatformLink::getLastSyncedPlaytimeMinutes).sum() / 60.0;
        LocalDateTime lastSyncedAt = links.stream()
                .map(GamePlatformLink::getLastSyncedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        OnlinePlaytime onlinePlaytime = findOrNewOnlinePlaytime(game.getUserId(), game.getId());
        onlinePlaytime.setTotalHours(Math.max(onlinePlaytime.getTotalHours(), syncedTotalHours));
        onlinePlaytime.setLastSessionAt(lastSyncedAt);
        onlinePlaytimeRepository.save(onlinePlaytime);
    }

    /**
     * Adds hours played since the previous sync to a confirmed game.
     *
     * @return false when nothing was written (game without category yet)
     */
    public boolean addDelta(GamePlatformLink link, Game game, double hoursToAdd, LocalDateTime sessionAt) {
        GameCategory category = game.getCategory();
        if (category == null) {
            log.debug("GamePlatformLink {} has no category yet; delta not applied", link.getId());
            return false;
        }

        if (category == GameCategory.SINGLEPLAYER) {
            Optional<Experience> canonical = findCanonicalExperience(link)
                    .or(() -> adoptInProgressRun(link, game.getId()));
            if (canonical.isPresent()) {
                Experience experience = canonical.get();
                experience.setHours(experience.getHours() + hoursToAdd);
                experienceRepository.save(experience);
            } else {
                // Game confirmed outside the sync flow, linked by name, or its canonical run was
                // deleted: start a new canonical run holding only the hours tracked from now on.
                createCanonicalExperience(link, game.getId(), hoursToAdd);
            }
            return true;
        }

        OnlinePlaytime onlinePlaytime = findOrNewOnlinePlaytime(link.getUserId(), game.getId());
        onlinePlaytime.setTotalHours(onlinePlaytime.getTotalHours() + hoursToAdd);
        onlinePlaytime.setLastSessionAt(sessionAt);
        onlinePlaytimeRepository.save(onlinePlaytime);
        return true;
    }

    private Optional<Experience> findCanonicalExperience(GamePlatformLink link) {
        if (link.getExperienceId() == null) return Optional.empty();
        return experienceRepository.findByIdAndUserId(link.getExperienceId(), link.getUserId());
    }

    /** Adopts the game's only in-progress run on the link's platform as the link's canonical run. */
    private Optional<Experience> adoptInProgressRun(GamePlatformLink link, Long gameId) {
        Optional<Experience> run = findAdoptableRun(link, gameId);
        run.ifPresent(experience -> link.setExperienceId(experience.getId()));
        return run;
    }

    /**
     * The game's run the user is playing on the link's platform, when there is exactly one. With none or
     * several there is no telling which run the hours belong to, so a canonical run is created instead.
     */
    private Optional<Experience> findAdoptableRun(GamePlatformLink link, Long gameId) {
        ImportedRunSource source = ImportedRunSource.of(link.getPlatform());
        List<Experience> inProgress = experienceRepository.findAllByGameIdAndUserId(gameId, link.getUserId()).stream()
                .filter(run -> run.getStatus() == ExperienceStatus.EN_CURSO && source.isOnPlatform(run))
                .toList();
        return inProgress.size() == 1 ? Optional.of(inProgress.getFirst()) : Optional.empty();
    }

    private void createCanonicalExperience(GamePlatformLink link, Long gameId, double hours) {
        ImportedRunSource source = ImportedRunSource.of(link.getPlatform());
        Experience experience = experienceRepository.save(Experience.builder()
                .userId(link.getUserId())
                .gameId(gameId)
                .runLabel(source.runLabel())
                .status(ExperienceStatus.EN_CURSO)
                .hours(hours)
                .platform(platformService.requireByLegacyCode(source.platformLegacyCode()))
                .platinum(false)
                .fullCompletion(false)
                .replay(false)
                .notes(source.note())
                .build());
        link.setExperienceId(experience.getId());
    }

    private OnlinePlaytime findOrNewOnlinePlaytime(Long userId, Long gameId) {
        return onlinePlaytimeRepository.findByGameIdAndUserId(gameId, userId)
                .orElseGet(() -> OnlinePlaytime.builder().userId(userId).gameId(gameId).totalHours(0.0).build());
    }
}
