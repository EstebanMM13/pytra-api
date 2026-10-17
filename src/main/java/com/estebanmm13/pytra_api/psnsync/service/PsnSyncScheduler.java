package com.estebanmm13.pytra_api.psnsync.service;

import com.estebanmm13.pytra_api.error.PsnIntegrationException;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.psnsync.client.PsnApiClient;
import com.estebanmm13.pytra_api.psnsync.client.PsnAuthenticator;
import com.estebanmm13.pytra_api.psnsync.model.PsnLink;
import com.estebanmm13.pytra_api.psnsync.repository.PsnLinkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PsnSyncScheduler {

    /** During a PSN outage, stop hammering it after this many failures in a row. */
    static final int MAX_CONSECUTIVE_UNAVAILABLE = 3;

    private final PsnLinkRepository psnLinkRepository;
    private final PsnSyncService psnSyncService;
    private final PsnApiClient psnApiClient;
    private final PsnAuthenticator psnAuthenticator;
    /** Pause between two accounts: spreads the shared service account's requests over the run. */
    private final Duration pauseBetweenAccounts;

    @Autowired
    public PsnSyncScheduler(PsnLinkRepository psnLinkRepository,
                            PsnSyncService psnSyncService,
                            PsnApiClient psnApiClient,
                            PsnAuthenticator psnAuthenticator,
                            @Value("${psn.scheduler.pause-between-accounts:3s}") Duration pauseBetweenAccounts) {
        this.psnLinkRepository = psnLinkRepository;
        this.psnSyncService = psnSyncService;
        this.psnApiClient = psnApiClient;
        this.psnAuthenticator = psnAuthenticator;
        this.pauseBetweenAccounts = pauseBetweenAccounts;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logConfiguration() {
        if (!psnAuthenticator.isConfigured()) {
            log.info("PSN_NPSSO is not set: PlayStation Network sync is disabled");
        } else if (!psnAuthenticator.hasExpectedNpssoFormat()) {
            log.warn("PSN_NPSSO does not look like an NPSSO (64 characters expected); PSN sign-in will probably fail");
        }
    }

    /**
     * Every 12 hours (PSN rate-limits the single service account every user shares, so less often
     * than Steam). The 20-minute initial delay keeps a deploy/restart from immediately firing a full
     * sync of every linked account, and from overlapping the Steam scheduler's first run.
     */
    @Scheduled(initialDelay = 20, fixedRate = 720, timeUnit = TimeUnit.MINUTES)
    public void syncAllLinkedAccounts() {
        if (!psnApiClient.isConfigured()) {
            return;
        }
        int consecutiveUnavailable = 0;
        List<PsnLink> links = psnLinkRepository.findAllInScheduledSyncOrder();
        for (int i = 0; i < links.size(); i++) {
            PsnLink psnLink = links.get(i);
            if (i > 0 && !pause()) {
                return;
            }
            if (psnApiClient.isRateLimited()) {
                log.warn("PSN is rate limiting the service account; {} accounts left for the next run", links.size() - i);
                return;
            }
            try {
                psnSyncService.sync(psnLink.getUserId());
                consecutiveUnavailable = 0;
            } catch (PsnIntegrationException e) {
                if (PsnIntegrationException.UNAVAILABLE.equals(e.getCode())) {
                    if (psnApiClient.serviceAuthFailingSince() != null) {
                        // The service account is rejected: every account would fail the same way.
                        log.warn("PSN service account is not authenticated; aborting this scheduled run");
                        return;
                    }
                    consecutiveUnavailable++;
                    if (consecutiveUnavailable >= MAX_CONSECUTIVE_UNAVAILABLE) {
                        log.warn("PSN unavailable for {} accounts in a row; aborting this scheduled run",
                                consecutiveUnavailable);
                        return;
                    }
                    log.debug("Scheduled PSN sync failed for userId {}: {}", psnLink.getUserId(), e.getCode());
                    continue;
                }
                consecutiveUnavailable = 0;
                log.warn("Scheduled PSN sync failed for userId {}: {}", psnLink.getUserId(), e.getCode());
                if (PsnIntegrationException.RATE_LIMITED.equals(e.getCode())) {
                    // Every other account shares the same service account; the least recently synced
                    // ones go first next run.
                    return;
                }
            } catch (SteamIntegrationException e) {
                // The shared sync guard: a manual sync (Steam or PSN) of this user is running.
                consecutiveUnavailable = 0;
                log.debug("Scheduled PSN sync skipped for userId {}: {}", psnLink.getUserId(), e.getCode());
            } catch (Exception e) {
                // Class name only: messages from the HTTP layer may contain the bearer token.
                log.warn("Scheduled PSN sync failed for userId {}: {}", psnLink.getUserId(), e.getClass().getSimpleName());
            }
        }
    }

    /** @return false when the thread was interrupted (shutdown): the run stops. */
    private boolean pause() {
        if (pauseBetweenAccounts.isZero() || pauseBetweenAccounts.isNegative()) {
            return true;
        }
        try {
            Thread.sleep(pauseBetweenAccounts.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
