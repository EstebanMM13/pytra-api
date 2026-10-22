package com.estebanmm13.pytra_api.psnsync.dto;

import com.estebanmm13.pytra_api.platformsync.EnrichmentProgress;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * {@code configured} is false when the server has no PSN_NPSSO: nothing PSN-related works then.
 *
 * @param linkedGamesCount        PSN_PS4/PSN_PS5 links on the user's games (pending placeholders included)
 * @param pendingCount            pending games linked to a PSN title
 * @param ignoredCount            PSN titles the user ignored
 * @param serviceAuthError        admins only: {@code PSN_SERVICE_AUTH_FAILED} while Sony rejects the service
 *                                account (renew PSN_NPSSO); null when healthy and always null for other users
 * @param serviceAuthFailingSince admins only: when that started; null otherwise
 * @param enrichment              background IGDB fill of the user's pending games (Steam and PSN alike)
 */
public record PsnStatusDto(
        boolean linked,
        String accountId,
        String onlineId,
        LocalDateTime lastSyncAt,
        boolean configured,
        long linkedGamesCount,
        long pendingCount,
        long ignoredCount,
        String serviceAuthError,
        Instant serviceAuthFailingSince,
        EnrichmentProgress enrichment
) {
}
