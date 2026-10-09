package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.steamsync.model.SteamLink;
import com.estebanmm13.pytra_api.steamsync.repository.SteamLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SteamSyncScheduler {

    private final SteamLinkRepository steamLinkRepository;
    private final SteamSyncService steamSyncService;

    @Scheduled(fixedRate = 6, timeUnit = java.util.concurrent.TimeUnit.HOURS)
    public void syncAllLinkedAccounts() {
        for (SteamLink steamLink : steamLinkRepository.findAll()) {
            try {
                steamSyncService.sync(steamLink.getUserId());
            } catch (Exception e) {
                log.warn("Scheduled Steam sync failed for userId {}: {}", steamLink.getUserId(), e.getMessage());
            }
        }
    }
}
