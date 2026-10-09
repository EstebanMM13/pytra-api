package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.steamsync.client.SteamWebApiClient;
import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SteamSyncScheduler {

    /** During a Steam outage, stop hammering it after this many failures in a row. */
    static final int MAX_CONSECUTIVE_UNAVAILABLE = 3;

    private final SteamLinkRepository steamLinkRepository;
    private final SteamSyncService steamSyncService;
    private final SteamWebApiClient steamWebApiClient;

    @EventListener(ApplicationReadyEvent.class)
    public void logConfiguration() {
        if (!steamWebApiClient.isConfigured()) {
            log.info("STEAM_API_KEY is not set: Steam library sync is disabled (account linking still works)");
        }
    }

    /**
     * Every 6 hours. The 10-minute initial delay keeps a deploy/restart from immediately firing
     * a full sync of every linked account.
     */
    @Scheduled(initialDelay = 10, fixedRate = 360, timeUnit = TimeUnit.MINUTES)
    public void syncAllLinkedAccounts() {
        if (!steamWebApiClient.isConfigured()) {
            return;
        }
        int consecutiveUnavailable = 0;
        for (SteamLink steamLink : steamLinkRepository.findAll()) {
            try {
                steamSyncService.sync(steamLink.getUserId());
                consecutiveUnavailable = 0;
            } catch (SteamIntegrationException e) {
                if (SteamIntegrationException.UNAVAILABLE.equals(e.getCode())) {
                    consecutiveUnavailable++;
                    if (consecutiveUnavailable >= MAX_CONSECUTIVE_UNAVAILABLE) {
                        log.warn("Steam unavailable for {} accounts in a row; aborting this scheduled run",
                                consecutiveUnavailable);
                        return;
                    }
                    log.debug("Scheduled Steam sync failed for userId {}: {}", steamLink.getUserId(), e.getCode());
                    continue;
                }
                consecutiveUnavailable = 0;
                if (SteamIntegrationException.SYNC_IN_PROGRESS.equals(e.getCode())) {
                    log.debug("Scheduled Steam sync skipped for userId {}: manual sync running", steamLink.getUserId());
                    continue;
                }
                log.warn("Scheduled Steam sync failed for userId {}: {}", steamLink.getUserId(), e.getCode());
                if (SteamIntegrationException.RATE_LIMITED.equals(e.getCode())
                        || SteamIntegrationException.API_KEY_REJECTED.equals(e.getCode())) {
                    // Every other account would fail the same way; try again next run.
                    return;
                }
            } catch (Exception e) {
                // Class name only: messages from the HTTP layer may contain the API key.
                log.warn("Scheduled Steam sync failed for userId {}: {}", steamLink.getUserId(), e.getClass().getSimpleName());
            }
        }
    }
}
