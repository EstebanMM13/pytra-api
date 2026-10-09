package com.estebanmm13.pytra_api.steamsync.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents two Steam operations for the same user from overlapping (manual sync, scheduled sync,
 * unlink). In-memory on purpose: the API runs as a single instance; with several replicas this
 * would need a DB row lock on steam_links instead.
 */
@Component
public class SteamSyncGuard {

    private final Set<Long> busyUserIds = ConcurrentHashMap.newKeySet();

    /** @return true if the caller now owns the user's slot and MUST call {@link #release(Long)}. */
    public boolean tryAcquire(Long userId) {
        return busyUserIds.add(userId);
    }

    public void release(Long userId) {
        busyUserIds.remove(userId);
    }
}
