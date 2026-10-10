package com.estebanmm13.pytra_api.metadata.service;

import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.metadata.client.SteamStoreClient;
import com.estebanmm13.pytra_api.metadata.dto.SteamGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.SteamStoreSearchResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SteamStoreMetadataServiceTest {

    private static final SteamGameMetadataDto PORTAL_2 =
            new SteamGameMetadataDto(620, "Portal 2", "Valve", "Valve", null, List.of("Acción"), null);

    private final SteamStoreClient client = mock(SteamStoreClient.class);
    private final MutableClock clock = new MutableClock();
    private SteamStoreMetadataService service;

    @BeforeEach
    void setUp() {
        service = new SteamStoreMetadataService(client, clock);
    }

    @Test
    void searchTrimsTheQueryAndCachesCaseInsensitively() {
        List<SteamStoreSearchResultDto> hits = List.of(new SteamStoreSearchResultDto(620, "Portal 2", null));
        when(client.search("Portal", 10)).thenReturn(hits);

        assertThat(service.search("  Portal ")).isEqualTo(hits);
        assertThat(service.search("portal")).isEqualTo(hits);

        verify(client, times(1)).search(anyString(), anyInt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"a", "  a  "})
    void rejectsTooShortQueriesWithoutCallingSteam(String query) {
        assertInvalid(() -> service.search(query), InvalidRequestException.INVALID_SEARCH_QUERY);
        verify(client, never()).search(anyString(), anyInt());
    }

    @Test
    void rejectsTooLongQueries() {
        assertInvalid(() -> service.search("x".repeat(101)), InvalidRequestException.INVALID_SEARCH_QUERY);
    }

    @Test
    void detailsAreCachedUntilTheTtlExpires() {
        when(client.getDetails(620)).thenReturn(PORTAL_2);

        service.getDetails("620");
        clock.advance(Duration.ofHours(5));
        service.getDetails("620");
        verify(client, times(1)).getDetails(620);

        clock.advance(Duration.ofHours(2));
        assertThat(service.getDetails("620")).isEqualTo(PORTAL_2);
        verify(client, times(2)).getDetails(620);
    }

    @Test
    void failuresAreNotCached() {
        when(client.getDetails(620))
                .thenThrow(SteamIntegrationException.unavailable())
                .thenReturn(PORTAL_2);

        assertThatThrownBy(() -> service.getDetails("620")).isInstanceOf(SteamIntegrationException.class);
        assertThat(service.getDetails("620")).isEqualTo(PORTAL_2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "-1", "0", "1.5", "12345678901", "620 "})
    void rejectsInvalidAppIdsWithoutCallingSteam(String appId) {
        assertInvalid(() -> service.getDetails(appId), InvalidRequestException.INVALID_STEAM_APP_ID);
        verify(client, never()).getDetails(anyLong());
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(InvalidRequestException.class).hasMessage(code);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
