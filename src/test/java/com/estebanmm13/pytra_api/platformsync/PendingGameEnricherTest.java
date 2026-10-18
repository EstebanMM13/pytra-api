package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.error.IgdbIntegrationException;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.metadata.client.IgdbClient;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameCandidateDto;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;
import com.estebanmm13.pytra_api.platformsync.PendingGameEnrichmentWriter.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PendingGameEnricherTest {

    private static final long USER_ID = 1L;
    private static final Set<Integer> PS5 = Set.of(167);
    private static final PendingGameEnrichmentWriter.Applied APPLIED =
            new PendingGameEnrichmentWriter.Applied(true, null, null);

    private final IgdbClient igdbClient = mock(IgdbClient.class);
    private final PendingGameEnrichmentWriter writer = mock(PendingGameEnrichmentWriter.class);
    private final List<Duration> sleeps = new ArrayList<>();
    private final List<Genre> genres = List.of(Genre.builder().id(1L).name("AVENTURA").build());

    private PendingGameEnricher enricher;

    @BeforeEach
    void setUp() {
        when(igdbClient.isEnabled()).thenReturn(true);
        when(writer.findAllGenres()).thenReturn(genres);
        when(writer.findGamesWithoutLookup(USER_ID)).thenReturn(List.of(5L));
        when(writer.apply(anyLong(), anyLong(), any(), anyList())).thenReturn(APPLIED);
        enricher = enricher(true);
    }

    @Test
    void confidentMatchIsAppliedWithTheCleanNameAsSearchTerm() {
        IgdbGameCandidateDto gta = candidate(1020, "Grand Theft Auto V");
        when(writer.findLookups(USER_ID, List.of(5L)))
                .thenReturn(List.of(new Lookup(5L, "Grand Theft Auto V (PlayStation®5)", PS5)));
        when(igdbClient.searchCandidates("Grand Theft Auto V", PS5, PendingGameEnricher.SEARCH_LIMIT))
                .thenReturn(List.of(gta));

        enricher.enqueue(USER_ID);

        verify(writer).apply(USER_ID, 5L, gta, genres);
        // apply() stamps the lookup itself; nothing else to mark.
        verify(writer).markLookedUp(USER_ID, List.of());
    }

    @Test
    void ambiguousResultsAreNotApplied() {
        when(writer.findLookups(USER_ID, List.of(5L))).thenReturn(List.of(new Lookup(5L, "Doom", PS5)));
        when(igdbClient.searchCandidates(anyString(), anySet(), anyInt()))
                .thenReturn(List.of(candidate(1, "DOOM"), candidate(2, "Doom")));

        enricher.enqueue(USER_ID);

        verify(writer, never()).apply(anyLong(), anyLong(), any(), anyList());
        // Asked once: no confident match is still an answer, so no later sync asks again.
        verify(writer).markLookedUp(USER_ID, List.of(5L));
    }

    @Test
    void disabledIgdbOrToggleSkipsSilently() {
        when(igdbClient.isEnabled()).thenReturn(false);
        enricher.enqueue(USER_ID);

        when(igdbClient.isEnabled()).thenReturn(true);
        enricher(false).enqueue(USER_ID);

        verifyNoInteractions(writer);
        verify(igdbClient, never()).searchCandidates(anyString(), anySet(), anyInt());
    }

    @Test
    void gamesWithNothingToLookUpAreMarkedWithoutCallingIgdb() {
        when(writer.findGamesWithoutLookup(USER_ID)).thenReturn(List.of(5L, 6L));
        when(writer.findLookups(USER_ID, List.of(5L, 6L))).thenReturn(List.of());

        enricher.enqueue(USER_ID);

        verify(igdbClient, never()).searchCandidates(anyString(), anySet(), anyInt());
        verify(writer).markLookedUp(eq(USER_ID), argThat(ids -> Set.copyOf(ids).equals(Set.of(5L, 6L))));
    }

    @Test
    void oneRunPerUserWaitsInTheQueue() {
        List<Runnable> queued = new ArrayList<>();
        ExecutorService holding = new InlineExecutor() {
            @Override
            public void execute(Runnable command) {
                queued.add(command);
            }
        };
        PendingGameEnricher deferred = new PendingGameEnricher(igdbClient, writer, true, holding, sleeps::add);

        deferred.enqueue(USER_ID);
        deferred.enqueue(USER_ID);
        deferred.enqueue(2L);
        assertThat(queued).hasSize(2);

        queued.getFirst().run();
        deferred.enqueue(USER_ID);
        assertThat(queued).hasSize(3);
    }

    @Test
    void aCappedRunQueuesTheNextOneSoTheBacklogDrains() {
        List<Long> ids = LongStream.rangeClosed(1, 250).boxed().toList();
        // The 200 games of the first run get stamped, so the second run only sees the other 50.
        when(writer.findGamesWithoutLookup(USER_ID)).thenReturn(ids, ids.subList(200, 250));

        enricher.enqueue(USER_ID);

        verify(writer, times(2)).findGamesWithoutLookup(USER_ID);
        verify(writer, times(10 + 3)).findLookups(eq(USER_ID), anyList());
    }

    @Test
    void aCappedRunWithoutProgressDoesNotQueueAnother() {
        List<Long> ids = LongStream.rangeClosed(1, 250).boxed().toList();
        when(writer.findGamesWithoutLookup(USER_ID)).thenReturn(ids);
        doThrow(new org.springframework.dao.QueryTimeoutException("slow"))
                .when(writer).markLookedUp(eq(USER_ID), anyList());

        enricher.enqueue(USER_ID);

        verify(writer, times(1)).findGamesWithoutLookup(USER_ID);
    }

    @Test
    void stopsBetweenBatchesOnceTheWorkerIsShutDown() {
        List<Long> ids = LongStream.rangeClosed(1, 45).boxed().toList();
        when(writer.findGamesWithoutLookup(USER_ID)).thenReturn(ids);
        InlineExecutor executor = new InlineExecutor();
        PendingGameEnricher stopping = new PendingGameEnricher(igdbClient, writer, true, executor, sleeps::add);
        when(writer.findLookups(USER_ID, ids.subList(0, 20))).thenAnswer(inv -> {
            executor.shutdown();
            return List.of();
        });

        stopping.run(USER_ID);

        verify(writer).findLookups(eq(USER_ID), anyList());
    }

    @Test
    void gamesAreProcessedInBatches() {
        List<Long> ids = LongStream.rangeClosed(1, 45).boxed().toList();
        when(writer.findGamesWithoutLookup(USER_ID)).thenReturn(ids);

        enricher.enqueue(USER_ID);

        verify(writer).findLookups(USER_ID, ids.subList(0, 20));
        verify(writer).findLookups(USER_ID, ids.subList(20, 40));
        verify(writer).findLookups(USER_ID, ids.subList(40, 45));
    }

    @Test
    void requestsAreSpacedToStayUnderTheIgdbRateLimit() {
        when(writer.findLookups(eq(USER_ID), anyList())).thenReturn(List.of(
                new Lookup(1L, "A", PS5), new Lookup(2L, "B", PS5), new Lookup(3L, "C", PS5)));
        when(igdbClient.searchCandidates(anyString(), anySet(), anyInt())).thenReturn(List.of());

        enricher.enqueue(USER_ID);

        verify(igdbClient, times(3)).searchCandidates(anyString(), anySet(), anyInt());
        // No wait before the first request; the next two wait for (almost) the whole spacing.
        assertThat(sleeps).hasSize(2)
                .allSatisfy(d -> assertThat(d).isPositive().isLessThanOrEqualTo(PendingGameEnricher.REQUEST_SPACING));
    }

    @Test
    void igdbFailuresAreLoggedAndTheRunStopsAfterSeveralInARow() {
        when(writer.findGamesWithoutLookup(USER_ID)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(writer.findLookups(eq(USER_ID), anyList())).thenReturn(List.of(
                new Lookup(1L, "A", PS5), new Lookup(2L, "B", PS5), new Lookup(3L, "C", PS5),
                new Lookup(4L, "D", PS5)));
        when(igdbClient.searchCandidates(anyString(), anySet(), anyInt()))
                .thenThrow(IgdbIntegrationException.unavailable());

        assertThatCode(() -> enricher.enqueue(USER_ID)).doesNotThrowAnyException();

        verify(igdbClient, times(PendingGameEnricher.MAX_CONSECUTIVE_FAILURES))
                .searchCandidates(anyString(), anySet(), anyInt());
        verify(writer, never()).apply(anyLong(), anyLong(), any(), anyList());
        // Failed lookups stay unmarked, so the next sync retries them.
        verify(writer).markLookedUp(USER_ID, List.of());
    }

    @Test
    void aFailingSaveDoesNotStopTheOtherGames() {
        when(writer.findGamesWithoutLookup(USER_ID)).thenReturn(List.of(1L, 2L));
        IgdbGameCandidateDto a = candidate(1, "A");
        IgdbGameCandidateDto b = candidate(2, "B");
        when(writer.findLookups(eq(USER_ID), anyList()))
                .thenReturn(List.of(new Lookup(1L, "A", PS5), new Lookup(2L, "B", PS5)));
        when(igdbClient.searchCandidates(eq("A"), anySet(), anyInt())).thenReturn(List.of(a));
        when(igdbClient.searchCandidates(eq("B"), anySet(), anyInt())).thenReturn(List.of(b));
        when(writer.apply(USER_ID, 1L, a, genres))
                .thenThrow(new DataIntegrityViolationException("value too long"));

        enricher.enqueue(USER_ID);

        verify(writer).apply(USER_ID, 2L, b, genres);
        // A deterministic failure is stamped, so the same game does not block every later run.
        verify(writer).markLookedUp(USER_ID, List.of(1L));
    }

    @Test
    void aTransientSaveFailureIsLeftForALaterSync() {
        IgdbGameCandidateDto a = candidate(1, "A");
        when(writer.findGamesWithoutLookup(USER_ID)).thenReturn(List.of(1L));
        when(writer.findLookups(USER_ID, List.of(1L))).thenReturn(List.of(new Lookup(1L, "A", PS5)));
        when(igdbClient.searchCandidates(eq("A"), anySet(), anyInt())).thenReturn(List.of(a));
        when(writer.apply(USER_ID, 1L, a, genres)).thenThrow(new CannotCreateTransactionException("no connection"));

        enricher.enqueue(USER_ID);

        verify(writer).markLookedUp(USER_ID, List.of());
    }

    @Test
    void renameRunsAfterTheFillAndItsFailureKeepsTheFill() {
        IgdbGameCandidateDto a = candidate(1, "A");
        when(writer.findLookups(USER_ID, List.of(5L))).thenReturn(List.of(new Lookup(5L, "A™", PS5)));
        when(igdbClient.searchCandidates(eq("A"), anySet(), anyInt())).thenReturn(List.of(a));
        when(writer.apply(USER_ID, 5L, a, genres))
                .thenReturn(new PendingGameEnrichmentWriter.Applied(true, "A", "A™"));
        when(writer.renameIfUntouched(USER_ID, 5L, "A™", "A"))
                .thenThrow(new DataIntegrityViolationException("ux_games_user_id_name_lower"));

        assertThatCode(() -> enricher.enqueue(USER_ID)).doesNotThrowAnyException();

        verify(writer).renameIfUntouched(USER_ID, 5L, "A™", "A");
        verify(writer).markLookedUp(USER_ID, List.of());
    }

    @Test
    void transientFailuresAreTheOnlyOnesRetried() {
        assertThat(PendingGameEnricher.isTransient(new CannotCreateTransactionException("x"))).isTrue();
        assertThat(PendingGameEnricher.isTransient(new org.springframework.dao.QueryTimeoutException("x"))).isTrue();
        assertThat(PendingGameEnricher.isTransient(new DataIntegrityViolationException("x"))).isFalse();
        assertThat(PendingGameEnricher.isTransient(new IllegalStateException("x"))).isFalse();
    }

    @Test
    void enqueueNeverThrowsEvenWhenTheWorkerRejectsTheRun() {
        ExecutorService rejecting = new InlineExecutor() {
            @Override
            public void execute(Runnable command) {
                throw new java.util.concurrent.RejectedExecutionException("full");
            }
        };
        PendingGameEnricher full = new PendingGameEnricher(igdbClient, writer, true, rejecting, sleeps::add);

        assertThatCode(() -> full.enqueue(USER_ID)).doesNotThrowAnyException();
        assertThatCode(() -> full.enqueue(USER_ID)).doesNotThrowAnyException();
    }

    private PendingGameEnricher enricher(boolean enabled) {
        return new PendingGameEnricher(igdbClient, writer, enabled, new InlineExecutor(), sleeps::add);
    }

    private static IgdbGameCandidateDto candidate(long id, String name) {
        IgdbGameMetadataDto metadata = new IgdbGameMetadataDto(id, name, "Dev", null, null, List.of(), null, List.of());
        return new IgdbGameCandidateDto(metadata, PS5, Set.of(1));
    }

    /** Runs the work on the calling thread, so tests see its effects right after enqueue returns. */
    private static class InlineExecutor extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
