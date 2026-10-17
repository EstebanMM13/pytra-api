package com.estebanmm13.pytra_api.steamsync.service;

import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Prevents platform-sync operations for the same user from overlapping: manual sync, scheduled sync,
 * unlink, switching to another Steam/PSN account, confirming and ignoring pending games, and account
 * deletion. Steam and PSN share this one slot per user: both write the same pending games, platform
 * links and playtime rows.
 * In-memory on purpose: the API runs as a single instance; with several replicas this would
 * need a DB row lock on steam_links instead.
 *
 * <p>Callers must hold the slot until their transaction has COMMITTED, so the guarded work must
 * open and finish its own transaction inside {@link #runExclusive} (never run it from a
 * {@code @Transactional} method, which would commit after the release).
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

    /**
     * Runs {@code work} holding the user's slot.
     *
     * @throws SteamIntegrationException SYNC_IN_PROGRESS when another sync operation for the user is running
     */
    public <T> T runExclusive(Long userId, Supplier<T> work) {
        if (!tryAcquire(userId)) {
            throw SteamIntegrationException.syncInProgress();
        }
        try {
            return work.get();
        } finally {
            release(userId);
        }
    }
}
