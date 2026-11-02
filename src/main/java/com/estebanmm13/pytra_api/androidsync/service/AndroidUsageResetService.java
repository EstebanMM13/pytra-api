package com.estebanmm13.pytra_api.androidsync.service;

import com.estebanmm13.pytra_api.androidsync.dto.AndroidUsageResetResponseDto;
import com.estebanmm13.pytra_api.androidsync.model.AndroidUsageDay;
import com.estebanmm13.pytra_api.androidsync.repository.AndroidUsageDayRepository;
import com.estebanmm13.pytra_api.diary.model.DiaryEntry;
import com.estebanmm13.pytra_api.diary.model.DiaryEntryGame;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryRepository;
import com.estebanmm13.pytra_api.diary.service.SyncedPlayRecorder;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.service.experienceService.ExperienceService;
import com.estebanmm13.pytra_api.experiences.service.hybridPlaytime.HybridPlaytimeReconciler;
import com.estebanmm13.pytra_api.experiences.service.onlinePlaytimeService.OnlinePlaytimeService;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.platforms.service.PlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Undoes everything the Android sync added for the user, so the next sync imports the days again from
 * scratch (used after a bad measurement). For each Android link, the minutes it applied
 * ({@link GamePlatformLink#getLastSyncedPlaytimeMinutes}, the sum of every applied delta) are taken back from
 * where {@code ExternalPlaytimeWriter} put them: the link's canonical run (SINGLEPLAYER), the online playtime
 * on Móvil (ONLINE) or the hybrid share (HYBRID). Each recorded day's minutes leave the game's auto line in the
 * diary (removed when nothing is left; a line the user wrote is never touched). Then the link restarts at 0 and
 * its baselines are deleted. Links stay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AndroidUsageResetService {

    /** What rounding leaves on an auto line after taking the Android minutes out: treated as nothing. */
    static final BigDecimal LINE_RESIDUE = new BigDecimal("0.05");

    private final GamePlatformLinkRepository linkRepository;
    private final AndroidUsageDayRepository usageDayRepository;
    private final DiaryEntryRepository diaryEntryRepository;
    private final ExperienceService experienceService;
    private final OnlinePlaytimeService onlinePlaytimeService;
    private final HybridPlaytimeReconciler hybridPlaytimeReconciler;
    private final PlatformService platformService;

    @Transactional
    public AndroidUsageResetResponseDto reset(Long userId) {
        List<GamePlatformLink> links = linkRepository.findAllByUserIdAndPlatformForUpdate(userId, ExternalPlatform.ANDROID);
        long minutesRemoved = 0;
        for (GamePlatformLink link : links) {
            minutesRemoved += takeBackHours(link);
            takeBackDiary(userId, link);
            link.setLastSyncedPlaytimeMinutes(0L);
            link.setLastSyncedAt(LocalDateTime.now());
            usageDayRepository.deleteAllOf(userId, link.getExternalId());
        }
        return new AndroidUsageResetResponseDto(links.size(), minutesRemoved);
    }

    /** Takes the link's applied minutes back from the game's hours; answers the minutes taken back. */
    private long takeBackHours(GamePlatformLink link) {
        long minutes = link.getLastSyncedPlaytimeMinutes() == null ? 0 : link.getLastSyncedPlaytimeMinutes();
        Game game = link.getGame();
        if (minutes <= 0 || game.getReviewStatus() != ReviewStatus.CONFIRMED || game.getCategory() == null) {
            return 0;
        }
        BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
        try {
            if (game.getCategory() == GameCategory.SINGLEPLAYER) {
                if (link.getExperienceId() == null) {
                    return 0;
                }
                experienceService.addHours(link.getExperienceId(), hours.negate(), link.getUserId());
            } else if (game.getCategory() == GameCategory.ONLINE) {
                Long mobile = platformService.requireByLegacyCode("MOBILE").getId();
                onlinePlaytimeService.addHours(game.getId(), hours.negate(), mobile, link.getUserId());
            } else {
                // HYBRID: the online hours moved by the change of the synced share; move them back.
                link.setLastSyncedPlaytimeMinutes(0L);
                hybridPlaytimeReconciler.applySyncedChange(link, minutes, LocalDateTime.now());
            }
        } catch (ResourceNotFoundException e) {
            // The run or the online record was deleted by the user: nothing left to take back.
            log.debug("Android reset of link {}: {}", link.getId(), e.getMessage());
            return 0;
        }
        return minutes;
    }

    /** Takes each recorded day's minutes out of the game's auto diary line. */
    private void takeBackDiary(Long userId, GamePlatformLink link) {
        Long gameId = link.getGame().getId();
        for (AndroidUsageDay day : usageDayRepository.findAllByUserIdAndPackageName(userId, link.getExternalId())) {
            DiaryEntry entry = diaryEntryRepository.findForUpdate(userId, day.getUsageDate()).orElse(null);
            if (entry == null) {
                continue;
            }
            DiaryEntryGame line = entry.getGames().stream()
                    .filter(game -> gameId.equals(game.getGameId()) && game.isAutoSynced())
                    .findFirst()
                    .orElse(null);
            if (line == null) {
                continue;
            }
            BigDecimal left = line.getHours().subtract(SyncedPlayRecorder.hoursOf(day.getMinutesRecorded()));
            if (left.compareTo(LINE_RESIDUE) <= 0) {
                entry.getGames().remove(line);
            } else {
                line.setHours(left);
            }
            if (entry.getGames().isEmpty() && (entry.getNote() == null || entry.getNote().isBlank())) {
                diaryEntryRepository.delete(entry);
            } else {
                diaryEntryRepository.save(entry);
            }
        }
    }
}
