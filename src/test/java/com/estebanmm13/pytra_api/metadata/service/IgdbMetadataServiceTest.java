package com.estebanmm13.pytra_api.metadata.service;

import com.estebanmm13.pytra_api.error.IgdbIntegrationException;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.metadata.client.IgdbClient;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.IgdbSearchResultDto;
import com.estebanmm13.pytra_api.metadata.dto.MetadataProvidersDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
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

class IgdbMetadataServiceTest {

    private static final IgdbGameMetadataDto KNACK =
            new IgdbGameMetadataDto(7, "Knack", "SIE Japan Studio", "Sony", null, List.of("Platform"), null, List.of());

    private final IgdbClient client = mock(IgdbClient.class);
    private IgdbMetadataService service;

    @BeforeEach
    void setUp() {
        when(client.isEnabled()).thenReturn(true);
        service = new IgdbMetadataService(client, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void providersReportIgdbAvailability() {
        assertThat(service.providers()).isEqualTo(new MetadataProvidersDto(true, true));
        when(client.isEnabled()).thenReturn(false);
        assertThat(service.providers()).isEqualTo(new MetadataProvidersDto(false, true));
    }

    @Test
    void disabledClientAnswersDisabledBeforeValidating() {
        when(client.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.search("x")).isInstanceOfSatisfying(IgdbIntegrationException.class,
                e -> assertThat(e.getCode()).isEqualTo(IgdbIntegrationException.DISABLED));
        assertThatThrownBy(() -> service.getDetails("abc")).isInstanceOfSatisfying(IgdbIntegrationException.class,
                e -> assertThat(e.getCode()).isEqualTo(IgdbIntegrationException.DISABLED));
        verify(client, never()).search(anyString(), anyInt());
    }

    @Test
    void searchTrimsTheQueryAndCachesCaseInsensitively() {
        List<IgdbSearchResultDto> hits = List.of(new IgdbSearchResultDto(7, "Knack", 2013, null, List.of("PS4")));
        when(client.search("Knack", 10)).thenReturn(hits);

        assertThat(service.search("  Knack ")).isEqualTo(hits);
        assertThat(service.search("knack")).isEqualTo(hits);

        verify(client, times(1)).search(anyString(), anyInt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"a", "  a  "})
    void rejectsTooShortQueries(String query) {
        assertThatThrownBy(() -> service.search(query)).isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.INVALID_SEARCH_QUERY);
        verify(client, never()).search(anyString(), anyInt());
    }

    @Test
    void rejectsTooLongQueries() {
        assertThatThrownBy(() -> service.search("x".repeat(101))).isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.INVALID_SEARCH_QUERY);
    }

    @Test
    void detailsAreCachedButFailuresAreNot() {
        when(client.getDetails(7))
                .thenThrow(IgdbIntegrationException.unavailable())
                .thenReturn(KNACK);

        assertThatThrownBy(() -> service.getDetails("7")).isInstanceOf(IgdbIntegrationException.class);
        assertThat(service.getDetails("7")).isEqualTo(KNACK);
        assertThat(service.getDetails("7")).isEqualTo(KNACK);

        verify(client, times(2)).getDetails(7);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "-1", "0", "1.5", "1234567890123", "7 "})
    void rejectsInvalidIds(String id) {
        assertThatThrownBy(() -> service.getDetails(id)).isInstanceOf(InvalidRequestException.class)
                .hasMessage(InvalidRequestException.INVALID_IGDB_ID);
        verify(client, never()).getDetails(anyLong());
    }
}
