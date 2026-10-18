package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.metadata.client.IgdbClient;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameCandidateDto;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Fills the pending games a Steam/PSN sync just created with IGDB metadata (cover, genres, developer,
 * publisher, release date, suggested category), in the background, so they reach the review queue
 * already filled in.
 *
 * <p>The sync never waits on this nor fails because of it: {@link #enqueue} only hands the ids to a
 * single worker thread and swallows every error. The worker processes them in batches of
 * {@value #BATCH_SIZE}, one IGDB request per game, spaced so it stays under IGDB's 4 requests/s even
 * with the interactive autofill running alongside. Only CONFIDENT matches are applied (see
 * {@link IgdbPendingMatch}) and only to empty fields (see {@link PendingGameFill}).
 *
 * <p>Skipped silently when IGDB has no credentials or {@code app.igdb.pending-enrichment.enabled} is
 * false. In memory only: ids queued when the app stops are lost, and those games simply stay as imported.
 */
@Slf4j
@Component
public class PendingGameEnricher {

    static final int BATCH_SIZE = 20;
    /** At most ~3.3 requests/s, leaving room under IGDB's 4/s for users autofilling by hand. */
    static final Duration REQUEST_SPACING = Duration.ofMillis(300);
    /** IGDB hits read per game: enough to see a same-name remake or port next to the original. */
    static final int SEARCH_LIMIT = 10;
    /** After this many failed calls in a row IGDB is assumed down and the rest of the run is dropped. */
    static final int MAX_CONSECUTIVE_FAILURES = 3;
    /** Pending runs waiting for the worker (one run per sync that created games). */
    private static final int QUEUE_CAPACITY = 100;

    /** Pauses the worker; a seam for tests. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final IgdbClient igdbClient;
    private final PendingGameEnrichmentWriter writer;
    private final boolean enabled;
    private final ExecutorService executor;
    private final Sleeper sleeper;
    private long lastRequestNanos;
    private boolean anyRequest;

    @Autowired
    public PendingGameEnricher(IgdbClient igdbClient,
                               PendingGameEnrichmentWriter writer,
                               @Value("${app.igdb.pending-enrichment.enabled:true}") boolean enabled) {
        this(igdbClient, writer, enabled, newWorker(), duration -> Thread.sleep(duration));
    }

    PendingGameEnricher(IgdbClient igdbClient, PendingGameEnrichmentWriter writer, boolean enabled,
                        ExecutorService executor, Sleeper sleeper) {
        this.igdbClient = igdbClient;
        this.writer = writer;
        this.enabled = enabled;
        this.executor = executor;
        this.sleeper = sleeper;
    }

    private static ExecutorService newWorker() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "igdb-pending-enrichment");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /** Queues the enrichment of {@code gameIds} and returns at once. Never throws. */
    public void enqueue(Long userId, Collection<Long> gameIds) {
        try {
            if (gameIds == null || gameIds.isEmpty() || !enabled || !igdbClient.isEnabled()) return;
            List<Long> ids = List.copyOf(gameIds);
            executor.execute(() -> run(userId, ids));
        } catch (RejectedExecutionException e) {
            log.warn("IGDB enrichment queue full: {} pending games of userId {} stay as imported", gameIds.size(), userId);
        } catch (RuntimeException e) {
            log.warn("IGDB enrichment for userId {} could not be queued: {}", userId, e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** Worker body: every batch, then stop early if IGDB keeps failing or the thread is interrupted. */
    void run(Long userId, List<Long> gameIds) {
        RunState state = new RunState();
        try {
            for (int from = 0; from < gameIds.size() && !state.aborted; from += BATCH_SIZE) {
                enrichBatch(userId, gameIds.subList(from, Math.min(from + BATCH_SIZE, gameIds.size())), state);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (RuntimeException e) {
            log.warn("IGDB enrichment for userId {} stopped: {}", userId, e.getClass().getSimpleName());
            return;
        }
        if (state.filled > 0 || state.failed > 0) {
            log.info("IGDB enrichment for userId {}: {} of {} pending games filled, {} lookups failed",
                    userId, state.filled, gameIds.size(), state.failed);
        }
    }

    private void enrichBatch(Long userId, List<Long> batch, RunState state) throws InterruptedException {
        List<PendingGameEnrichmentWriter.Lookup> lookups = writer.findLookups(userId, batch);
        if (lookups.isEmpty()) return;
        List<Genre> genres = writer.findAllGenres();

        for (PendingGameEnrichmentWriter.Lookup lookup : lookups) {
            String term = ImportedGameNames.cleanName(lookup.name());
            if (term.isEmpty()) continue;

            Optional<IgdbGameCandidateDto> match;
            try {
                pace();
                List<IgdbGameCandidateDto> candidates =
                        igdbClient.searchCandidates(term, lookup.igdbPlatformIds(), SEARCH_LIMIT);
                state.consecutiveFailures = 0;
                match = IgdbPendingMatch.select(lookup.name(), lookup.igdbPlatformIds(), candidates);
            } catch (RuntimeException e) {
                state.failed++;
                log.warn("IGDB lookup for pending game {} of userId {} failed: {}",
                        lookup.gameId(), userId, e.getClass().getSimpleName());
                if (++state.consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    log.warn("IGDB enrichment for userId {} aborted after {} failed lookups in a row",
                            userId, state.consecutiveFailures);
                    state.aborted = true;
                    return;
                }
                continue;
            }

            if (match.isEmpty()) continue;
            try {
                if (writer.apply(userId, lookup.gameId(), match.get(), genres)) state.filled++;
            } catch (RuntimeException e) {
                log.warn("IGDB enrichment of pending game {} of userId {} could not be saved: {}",
                        lookup.gameId(), userId, e.getClass().getSimpleName());
            }
        }
    }

    /** Waits until {@link #REQUEST_SPACING} has passed since the previous IGDB request. */
    private void pace() throws InterruptedException {
        if (anyRequest) {
            long waitNanos = REQUEST_SPACING.toNanos() - (System.nanoTime() - lastRequestNanos);
            if (waitNanos > 0) sleeper.sleep(Duration.ofNanos(waitNanos));
        }
        anyRequest = true;
        lastRequestNanos = System.nanoTime();
    }

    private static final class RunState {
        int filled;
        int failed;
        int consecutiveFailures;
        boolean aborted;
    }
}
