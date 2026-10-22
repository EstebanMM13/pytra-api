package com.estebanmm13.pytra_api.platformsync;

/**
 * Background IGDB enrichment of one user's pending games, as reported to the client so it can refresh
 * the review queue while games are being filled ({@link PendingGameEnricher}).
 *
 * @param active    a run is queued or running for the user
 * @param processed pending games looked at so far (filled or not)
 * @param total     pending games the active run(s) will look at; 0 while the run is still queued
 */
public record EnrichmentProgress(boolean active, int processed, int total) {

    public static final EnrichmentProgress IDLE = new EnrichmentProgress(false, 0, 0);
}
