package com.estebanmm13.pytra_api.metadata.service;

import com.estebanmm13.pytra_api.error.IgdbIntegrationException;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.metadata.client.IgdbClient;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.IgdbSearchResultDto;
import com.estebanmm13.pytra_api.metadata.dto.MetadataProvidersDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Read-only IGDB lookups for the "autofill" action of the game form. IGDB data is the same for every
 * user, so results are cached globally, which also keeps us well under IGDB's 4 requests/s.
 */
@Service
public class IgdbMetadataService {

    static final int MIN_QUERY_LENGTH = SteamStoreMetadataService.MIN_QUERY_LENGTH;
    static final int MAX_QUERY_LENGTH = SteamStoreMetadataService.MAX_QUERY_LENGTH;
    static final int MAX_RESULTS = 10;

    private final IgdbClient igdbClient;
    private final TtlCache<String, List<IgdbSearchResultDto>> searchCache;
    private final TtlCache<Long, IgdbGameMetadataDto> detailsCache;

    @Autowired
    public IgdbMetadataService(IgdbClient igdbClient) {
        this(igdbClient, Clock.systemUTC());
    }

    IgdbMetadataService(IgdbClient igdbClient, Clock clock) {
        this.igdbClient = igdbClient;
        this.searchCache = new TtlCache<>(200, Duration.ofMinutes(10), clock);
        this.detailsCache = new TtlCache<>(500, Duration.ofHours(6), clock);
    }

    public MetadataProvidersDto providers() {
        return new MetadataProvidersDto(igdbClient.isEnabled(), true);
    }

    public List<IgdbSearchResultDto> search(String query) {
        requireEnabled();
        String term = query == null ? "" : query.strip();
        if (term.length() < MIN_QUERY_LENGTH || term.length() > MAX_QUERY_LENGTH) {
            throw new InvalidRequestException(InvalidRequestException.INVALID_SEARCH_QUERY);
        }
        return searchCache.get(term.toLowerCase(Locale.ROOT), () -> igdbClient.search(term, MAX_RESULTS));
    }

    public IgdbGameMetadataDto getDetails(String igdbId) {
        requireEnabled();
        long id = parseIgdbId(igdbId);
        return detailsCache.get(id, () -> igdbClient.getDetails(id));
    }

    /** Checked first, so a deployment without credentials answers IGDB_DISABLED whatever the input. */
    private void requireEnabled() {
        if (!igdbClient.isEnabled()) throw IgdbIntegrationException.disabled();
    }

    /** IGDB game ids are positive integers; anything else is rejected before reaching IGDB. */
    static long parseIgdbId(String igdbId) {
        if (igdbId == null || !igdbId.matches("\\d{1,12}")) {
            throw new InvalidRequestException(InvalidRequestException.INVALID_IGDB_ID);
        }
        long id = Long.parseLong(igdbId);
        if (id <= 0) throw new InvalidRequestException(InvalidRequestException.INVALID_IGDB_ID);
        return id;
    }
}
