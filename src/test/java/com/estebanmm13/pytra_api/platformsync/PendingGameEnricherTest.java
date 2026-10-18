package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.error.IgdbIntegrationException;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.metadata.client.IgdbClient;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameCandidateDto;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;
import com.estebanmm13.pytra_api.platformsync.PendingGameEnrichmentWriter.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PendingGameEnricherTest {

    private static final long USER_ID = 1L;
    private static final Set<Integer> PS5 = Set.of(167);

    private final IgdbClient igdbClient = mock(IgdbClient.class);
    private final PendingGameEnrichmentWriter writer = mock(PendingGameEnrichmentWriter.class);
    private final List<Duration> sleeps = new ArrayList<>();
    private final List<Genre> genres = List.of(Genre.builder().id(1L).name("AVENTURA").build());

    private PendingGameEnricher enricher;

    @BeforeEach
    void setUp() {
        when(igdbClient.isEnabled()).thenReturn(true);
        when(writer.findAllGenres()).thenReturn(genres);
        enricher = enricher(true);
    }

    @Test
    void confidentMatchIsAppliedWithTheCleanNameAsSearchTerm() {
        IgdbGameCandidateDto gta = candidate(1020, "Grand Theft Auto V");
        when(writer.findLookups(USER_ID, List.of(5L)))
                .thenReturn(List.of(new Lookup(5L, "Grand Theft Auto V (PlayStation®5)", PS5)));
        when(igdbClient.searchCandidates("Grand Theft Auto V", PS5, PendingGameEnricher.SEARCH_LIMIT))
                .thenReturn(List.of(gta));

        enricher.enqueue(USER_ID, List.of(5L));

        verify(writer).apply(USER_ID, 5L, gta, genres);
    }

    @Test
    void ambiguousResultsAreNotApplied() {
        when(writer.findLookups(USER_ID, List.of(5L))).thenReturn(List.of(new Lookup(5L, "Doom", PS5)));
        when(igdbClient.searchCandidates(anyString(), anySet(), anyInt()))
                .thenReturn(List.of(candidate(1, "DOOM"), candidate(2, "Doom")));

        enricher.enqueue(USER_ID, List.of(5L));

        verify(writer, never()).apply(anyLong(), anyLong(), any(), anyList());
    }

    @Test
    void disabledIgdbOrToggleSkipsSilently() {
        when(igdbClient.isEnabled()).thenReturn(false);
        enricher.enqueue(USER_ID, List.of(5L));

        when(igdbClient.isEnabled()).thenReturn(true);
        enricher(false).enqueue(USER_ID, List.of(5L));

        verifyNoInteractions(writer);
        verify(igdbClient, never()).searchCandidates(anyString(), anySet(), anyInt());
    }

    @Test
    void nothingCreatedQueuesNothing() {
        enricher.enqueue(USER_ID, List.of());

        verifyNoInteractions(writer);
    }

    @Test
    void gamesAreProcessedInBatches() {
        List<Long> ids = LongStream.rangeClosed(1, 45).boxed().toList();

        enricher.enqueue(USER_ID, ids);

        verify(writer).findLookups(USER_ID, ids.subList(0, 20));
        verify(writer).findLookups(USER_ID, ids.subList(20, 40));
        verify(writer).findLookups(USER_ID, ids.subList(40, 45));
    }

    @Test
    void requestsAreSpacedToStayUnderTheIgdbRateLimit() {
        when(writer.findLookups(eq(USER_ID), anyList())).thenReturn(List.of(
                new Lookup(1L, "A", PS5), new Lookup(2L, "B", PS5), new Lookup(3L, "C", PS5)));
        when(igdbClient.searchCandidates(anyString(), anySet(), anyInt())).thenReturn(List.of());

        enricher.enqueue(USER_ID, List.of(1L, 2L, 3L));

        verify(igdbClient, times(3)).searchCandidates(anyString(), anySet(), anyInt());
        // No wait before the first request; the next two wait for (almost) the whole spacing.
        assertThat(sleeps).hasSize(2)
                .allSatisfy(d -> assertThat(d).isPositive().isLessThanOrEqualTo(PendingGameEnricher.REQUEST_SPACING));
    }

    @Test
    void igdbFailuresAreLoggedAndTheRunStopsAfterSeveralInARow() {
        when(writer.findLookups(eq(USER_ID), anyList())).thenReturn(List.of(
                new Lookup(1L, "A", PS5), new Lookup(2L, "B", PS5), new Lookup(3L, "C", PS5),
                new Lookup(4L, "D", PS5)));
        when(igdbClient.searchCandidates(anyString(), anySet(), anyInt()))
                .thenThrow(IgdbIntegrationException.unavailable());

        assertThatCode(() -> enricher.enqueue(USER_ID, List.of(1L, 2L, 3L, 4L))).doesNotThrowAnyException();

        verify(igdbClient, times(PendingGameEnricher.MAX_CONSECUTIVE_FAILURES))
                .searchCandidates(anyString(), anySet(), anyInt());
        verify(writer, never()).apply(anyLong(), anyLong(), any(), anyList());
    }

    @Test
    void aFailingSaveDoesNotStopTheOtherGames() {
        IgdbGameCandidateDto a = candidate(1, "A");
        IgdbGameCandidateDto b = candidate(2, "B");
        when(writer.findLookups(eq(USER_ID), anyList()))
                .thenReturn(List.of(new Lookup(1L, "A", PS5), new Lookup(2L, "B", PS5)));
        when(igdbClient.searchCandidates(eq("A"), anySet(), anyInt())).thenReturn(List.of(a));
        when(igdbClient.searchCandidates(eq("B"), anySet(), anyInt())).thenReturn(List.of(b));
        when(writer.apply(USER_ID, 1L, a, genres)).thenThrow(new IllegalStateException("db"));

        enricher.enqueue(USER_ID, List.of(1L, 2L));

        verify(writer).apply(USER_ID, 2L, b, genres);
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

        assertThatCode(() -> full.enqueue(USER_ID, List.of(1L))).doesNotThrowAnyException();
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
