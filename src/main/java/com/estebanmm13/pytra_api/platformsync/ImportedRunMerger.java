package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.service.experienceService.ExperienceService;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Self-heal run by every Steam and PSN sync: folds an untouched imported canonical run ("Importado de …")
 * into the run the user is already playing on the same platform.
 *
 * <p>Before {@link ExternalPlaytimeWriter} adopted in-progress runs, linking a title to a game the user was
 * already playing created a separate imported run that took every later delta. When a CONFIRMED
 * SINGLEPLAYER game's link points at such a run, nobody edited it
 * ({@link ImportedRunSource#isUntouchedImportedRun}) and the game has exactly one other in-progress run on
 * that platform, the imported hours are added to that run (through the regular run update), the link and
 * the imported run's diary lines move to it, and the imported run is deleted. Idempotent: once merged the
 * link points at a run the user created, which is never untouched-imported.
 *
 * <p>One transaction per game, opened here (callers must not hold one); a failing game is logged and left
 * as it is.
 */
@Slf4j
@Component
public class ImportedRunMerger {

    private static final Set<ExternalPlatform> SYNCED_PLATFORMS =
            EnumSet.of(ExternalPlatform.STEAM, ExternalPlatform.PSN_PS4, ExternalPlatform.PSN_PS5);

    private final GameRepository gameRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final ExperienceRepository experienceRepository;
    private final ExperienceService experienceService;
    private final DiaryEntryGameRepository diaryEntryGameRepository;
    private final TransactionOperations transactionOperations;

    @Autowired
    public ImportedRunMerger(GameRepository gameRepository,
                             GamePlatformLinkRepository gamePlatformLinkRepository,
                             ExperienceRepository experienceRepository,
                             ExperienceService experienceService,
                             DiaryEntryGameRepository diaryEntryGameRepository,
                             PlatformTransactionManager transactionManager) {
        this(gameRepository, gamePlatformLinkRepository, experienceRepository, experienceService,
                diaryEntryGameRepository, new TransactionTemplate(transactionManager));
    }

    /** Test seam: runs every callback without a real transaction. */
    ImportedRunMerger(GameRepository gameRepository,
                      GamePlatformLinkRepository gamePlatformLinkRepository,
                      ExperienceRepository experienceRepository,
                      ExperienceService experienceService,
                      DiaryEntryGameRepository diaryEntryGameRepository,
                      TransactionOperations transactionOperations) {
        this.gameRepository = gameRepository;
        this.gamePlatformLinkRepository = gamePlatformLinkRepository;
        this.experienceRepository = experienceRepository;
        this.experienceService = experienceService;
        this.diaryEntryGameRepository = diaryEntryGameRepository;
        this.transactionOperations = transactionOperations;
    }

    /**
     * Merges every eligible imported run of the user.
     *
     * @return how many imported runs were merged
     */
    public int mergeAll(Long userId) {
        List<Long> gameIds;
        try {
            gameIds = transactionOperations.execute(status -> candidateGameIds(userId));
        } catch (RuntimeException e) {
            // A self-heal never fails the sync it runs in.
            log.warn("Imported run merge for userId {} failed: {}", userId, e.getClass().getSimpleName());
            return 0;
        }
        if (gameIds == null || gameIds.isEmpty()) return 0;

        int merged = 0;
        for (Long gameId : gameIds) {
            try {
                Integer count = transactionOperations.execute(status -> mergeGame(userId, gameId));
                merged += count == null ? 0 : count;
            } catch (RuntimeException e) {
                // Class name only: driver messages can echo the offending values.
                log.warn("Imported run merge of game {} for userId {} failed: {}",
                        gameId, userId, e.getClass().getSimpleName());
            }
        }
        if (merged > 0) {
            log.info("Sync for userId {} merged {} imported runs into in-progress runs", userId, merged);
        }
        return merged;
    }

    private List<Long> candidateGameIds(Long userId) {
        return gamePlatformLinkRepository.findAllByUserIdAndPlatformInWithGame(userId, SYNCED_PLATFORMS).stream()
                .filter(link -> link.getExperienceId() != null && isEligible(link.getGame()))
                .map(link -> link.getGame().getId())
                .distinct()
                .sorted()
                .toList();
    }

    /** Merges the game's eligible imported runs; must run inside a transaction. */
    int mergeGame(Long userId, Long gameId) {
        Optional<Game> game = gameRepository.findForUpdateByIdAndUserId(gameId, userId);
        if (game.isEmpty() || !isEligible(game.get())) return 0;

        List<Experience> runs = experienceRepository.findAllByGameIdAndUserId(gameId, userId);
        Map<Long, Experience> runsById = runs.stream()
                .collect(Collectors.toMap(Experience::getId, Function.identity()));

        int merged = 0;
        for (GamePlatformLink link : gamePlatformLinkRepository.findAllByUserIdAndGameId(userId, gameId)) {
            if (!SYNCED_PLATFORMS.contains(link.getPlatform()) || link.getExperienceId() == null) continue;
            Experience imported = runsById.get(link.getExperienceId());
            ImportedRunSource source = ImportedRunSource.of(link.getPlatform());
            if (imported == null || !source.isUntouchedImportedRun(imported)) continue;

            List<Experience> targets = runs.stream()
                    .filter(run -> !Objects.equals(run.getId(), imported.getId())
                            && runsById.containsKey(run.getId())
                            && run.getStatus() == ExperienceStatus.EN_CURSO
                            && source.isOnPlatform(run))
                    .toList();
            if (targets.size() != 1) continue;

            merge(userId, link, imported, targets.getFirst());
            runsById.remove(imported.getId());
            merged++;
        }
        return merged;
    }

    private void merge(Long userId, GamePlatformLink link, Experience imported, Experience target) {
        double importedHours = imported.getHours() != null ? imported.getHours() : 0.0;
        if (importedHours > 0) {
            experienceService.addHours(target.getId(), BigDecimal.valueOf(importedHours), userId);
        }
        link.setExperienceId(target.getId());
        // The diary lines follow the hours they counted: deleting the imported run must not subtract them.
        diaryEntryGameRepository.repointRun(imported.getId(), target.getId());
        experienceRepository.delete(imported);
    }

    private static boolean isEligible(Game game) {
        return game != null
                && game.getReviewStatus() == ReviewStatus.CONFIRMED
                && game.getCategory() == GameCategory.SINGLEPLAYER;
    }
}
