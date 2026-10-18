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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Fills the pending games of a Steam/PSN user with IGDB metadata (cover, name, genres, developer,
 * publisher, release date, suggested category), in the background, so they reach the review queue
 * already filled in.
 *
 * <p>Every sync calls {@link #enqueue}, which only queues a run for the user and never throws, so the
 * sync never waits on IGDB nor fails because of it. A run reads the user's pending games IGDB was never
 * asked about ({@code games.igdb_lookup_at IS NULL}), so games from an earlier run that was dropped (IGDB
 * down, app restarted, import interrupted) are caught up by the next sync, while a game without a
 * confident match is asked about only once. At most one run per user waits in the queue.
 *
 * <p>One worker thread, one IGDB request per game, spaced so it stays under IGDB's 4 requests/s even with
 * the interactive autofill running alongside; batches of {@value #BATCH_SIZE}. Only CONFIDENT matches are
 * applied (see {@link IgdbPendingMatch}), never over user data (see {@link PendingGameFill}).
 *
 * <p>Skipped silently when IGDB has no credentials or {@code app.igdb.pending-enrichment.enabled} is false.
 */
@Slf4j
@Component
public class PendingGameEnricher {

    static final int BATCH_SIZE = 20;
    /** Games looked up per run at most; the rest waits for the user's next sync. */
    static final int MAX_GAMES_PER_RUN = 200;
    /** At most ~3.3 requests/s, leaving room under IGDB's 4/s for users autofilling by hand. */
    static final Duration REQUEST_SPACING = Duration.ofMillis(300);
    /** IGDB hits read per game: enough to see a same-name remake or port next to the original. */
    static final int SEARCH_LIMIT = 10;
    /** After this many failed calls in a row IGDB is assumed down and the run stops (retried next sync). */
    static final int MAX_CONSECUTIVE_FAILURES = 3;
    /** How long shutdown waits for the current IGDB call to finish. */
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);

    /** Pauses the worker; a seam for tests. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final IgdbClient igdbClient;
    private final PendingGameEnrichmentWriter writer;
    private final boolean enabled;
    private final ExecutorService executor;
    private final Sleeper sleeper;
    /** Users with a run waiting in the queue: a second sync meanwhile adds nothing, that run covers it. */
    private final Set<Long> queuedUsers = ConcurrentHashMap.newKeySet();
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

    /** Unbounded queue: it holds at most one run per user (see {@link #queuedUsers}). */
    private static ExecutorService newWorker() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "igdb-pending-enrichment");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Queues a run over the user's pending games not looked up yet, and returns at once. Never throws. */
    public void enqueue(Long userId) {
        try {
            if (!enabled || !igdbClient.isEnabled() || !queuedUsers.add(userId)) return;
            try {
                executor.execute(() -> run(userId));
            } catch (RuntimeException e) {
                queuedUsers.remove(userId);
                throw e;
            }
        } catch (RejectedExecutionException e) {
            log.warn("IGDB enrichment for userId {} not queued: the worker is shut down", userId);
        } catch (RuntimeException e) {
            log.warn("IGDB enrichment for userId {} could not be queued: {}", userId, e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("IGDB enrichment worker did not stop within {}", SHUTDOWN_WAIT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Worker body: every batch, stopping early if IGDB keeps failing or the app is shutting down. */
    void run(Long userId) {
        // Removed first: a sync finishing while this run works queues the next one.
        queuedUsers.remove(userId);
        RunState state = new RunState();
        List<Long> gameIds;
        try {
            gameIds = writer.findGamesWithoutLookup(userId);
            if (gameIds.size() > MAX_GAMES_PER_RUN) gameIds = gameIds.subList(0, MAX_GAMES_PER_RUN);
            for (int from = 0; from < gameIds.size() && !state.aborted && !stopping(); from += BATCH_SIZE) {
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
        // Games with nothing to look up (gone, confirmed, no IGDB platform) are marked right away.
        Set<Long> done = new HashSet<>(batch);
        lookups.forEach(lookup -> done.remove(lookup.gameId()));
        List<Genre> genres = lookups.isEmpty() ? List.of() : writer.findAllGenres();

        try {
            for (PendingGameEnrichmentWriter.Lookup lookup : lookups) {
                if (stopping()) return;
                String term = ImportedGameNames.cleanName(lookup.name());
                if (term.isEmpty()) {
                    done.add(lookup.gameId());
                    continue;
                }

                Optional<IgdbGameCandidateDto> match;
                try {
                    pace();
                    List<IgdbGameCandidateDto> candidates =
                            igdbClient.searchCandidates(term, lookup.igdbPlatformIds(), SEARCH_LIMIT);
                    state.consecutiveFailures = 0;
                    match = IgdbPendingMatch.select(lookup.name(), lookup.igdbPlatformIds(), candidates);
                } catch (RuntimeException e) {
                    // Not marked: the game is retried on a later sync.
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

                if (match.isEmpty()) {
                    done.add(lookup.gameId());
                    continue;
                }
                try {
                    // apply() stamps the lookup itself, in the same transaction as the fill.
                    if (writer.apply(userId, lookup.gameId(), match.get(), genres)) state.filled++;
                } catch (RuntimeException e) {
                    // Not marked: retried on a later sync.
                    log.warn("IGDB enrichment of pending game {} of userId {} could not be saved: {}",
                            lookup.gameId(), userId, e.getClass().getSimpleName());
                }
            }
        } finally {
            markLookedUp(userId, done);
        }
    }

    private void markLookedUp(Long userId, Set<Long> gameIds) {
        try {
            writer.markLookedUp(userId, new ArrayList<>(gameIds));
        } catch (RuntimeException e) {
            log.warn("IGDB lookups of userId {} could not be recorded: {}", userId, e.getClass().getSimpleName());
        }
    }

    private boolean stopping() {
        return Thread.currentThread().isInterrupted() || executor.isShutdown();
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
