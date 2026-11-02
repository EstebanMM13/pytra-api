package com.estebanmm13.pytra_api.androidsync.service;

import com.estebanmm13.pytra_api.androidsync.dto.AndroidUsageDayDto;
import com.estebanmm13.pytra_api.androidsync.dto.AndroidUsageSyncResponseDto;
import com.estebanmm13.pytra_api.androidsync.model.AndroidUsageDay;
import com.estebanmm13.pytra_api.androidsync.repository.AndroidUsageDayRepository;
import com.estebanmm13.pytra_api.config.AppTimeZone;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.games.model.ExternalPlatform;
import com.estebanmm13.pytra_api.games.model.GamePlatformLink;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.platformsync.ExternalPlaytimeWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns the phone's daily usage totals into play, like a Steam/PSN sync: each linked package's new minutes go
 * through {@link ExternalPlaytimeWriter#applyObservedPlay} (canonical run, or online hours on "Móvil") with a
 * record-only diary line on that day.
 *
 * <p>The phone re-sends each day's whole total on every sync, so {@code android_usage_days} keeps the minutes
 * already recorded per package and day; only {@code total - recorded} is new. A delta under one minute is left
 * for a later sync (the baseline does not move). The day's baseline row is locked, so concurrent syncs never
 * count the same minutes twice.
 */
@Service
public class AndroidUsageSyncService {

    /** Android keeps about 7-10 days of usage events; older days are never accepted. */
    static final int MAX_DAYS_BACK = 14;
    static final int MAX_DAY_MINUTES = 1440;
    static final int MAX_ITEMS = 1000;

    private final GamePlatformLinkRepository linkRepository;
    private final AndroidUsageDayRepository usageDayRepository;
    private final ExternalPlaytimeWriter playtimeWriter;
    private final ZoneId appZone;
    private final Clock clock;

    @Autowired
    public AndroidUsageSyncService(GamePlatformLinkRepository linkRepository,
                                   AndroidUsageDayRepository usageDayRepository,
                                   ExternalPlaytimeWriter playtimeWriter, AppTimeZone appTimeZone) {
        this(linkRepository, usageDayRepository, playtimeWriter, appTimeZone.zone(), Clock.systemDefaultZone());
    }

    AndroidUsageSyncService(GamePlatformLinkRepository linkRepository, AndroidUsageDayRepository usageDayRepository,
                            ExternalPlaytimeWriter playtimeWriter, ZoneId appZone, Clock clock) {
        this.linkRepository = linkRepository;
        this.usageDayRepository = usageDayRepository;
        this.playtimeWriter = playtimeWriter;
        this.appZone = appZone;
        this.clock = clock;
    }

    @Transactional
    public AndroidUsageSyncResponseDto sync(Long userId, List<AndroidUsageDayDto> days) {
        if (days == null || days.size() > MAX_ITEMS) {
            throw new InvalidRequestException(InvalidRequestException.INVALID_ANDROID_USAGE);
        }
        Instant instant = clock.instant();
        // Server-zone timestamps like every other sync; calendar days in the users' zone.
        LocalDateTime now = LocalDateTime.ofInstant(instant, clock.getZone());
        LocalDate today = LocalDate.ofInstant(instant, appZone);
        Map<String, GamePlatformLink> links = linkRepository
                .findAllByUserIdAndPlatformForUpdate(userId, ExternalPlatform.ANDROID).stream()
                .collect(Collectors.toMap(GamePlatformLink::getExternalId, Function.identity()));

        int recorded = 0;
        for (AndroidUsageDayDto day : days) {
            if (record(userId, day, links, today, now)) {
                recorded++;
            }
        }
        return new AndroidUsageSyncResponseDto(recorded, days.size() - recorded);
    }

    /** True when the item added new play. */
    private boolean record(Long userId, AndroidUsageDayDto day, Map<String, GamePlatformLink> links,
                           LocalDate today, LocalDateTime now) {
        if (day == null || day.date() == null || day.packageName() == null || day.minutes() == null) {
            return false;
        }
        if (day.date().isAfter(today) || day.date().isBefore(today.minusDays(MAX_DAYS_BACK))
                || day.minutes() < 0 || day.minutes() > MAX_DAY_MINUTES) {
            return false;
        }
        GamePlatformLink link = links.get(day.packageName());
        if (link == null) {
            return false;
        }

        usageDayRepository.ensureExists(userId, day.packageName(), day.date());
        AndroidUsageDay baseline = usageDayRepository.findForUpdate(userId, day.packageName(), day.date())
                .orElseThrow(() -> new IllegalStateException("Android usage baseline missing after insert"));
        int delta = day.minutes() - baseline.getMinutesRecorded();
        if (delta < 1) {
            return false;
        }
        baseline.setMinutesRecorded(day.minutes());
        usageDayRepository.save(baseline);
        if (day.date().isBefore(linkedDay(link))) {
            // Play before the link existed is history: only the baseline moves (Steam/PSN never import it either).
            return false;
        }
        playtimeWriter.applyObservedPlay(link, delta, day.date(), now);
        return true;
    }

    /** First day (users' zone) whose play counts; a link without a creation time counts every day. */
    private LocalDate linkedDay(GamePlatformLink link) {
        if (link.getLinkedAt() == null) {
            return LocalDate.MIN;
        }
        return link.getLinkedAt().atZone(clock.getZone()).withZoneSameInstant(appZone).toLocalDate();
    }
}
