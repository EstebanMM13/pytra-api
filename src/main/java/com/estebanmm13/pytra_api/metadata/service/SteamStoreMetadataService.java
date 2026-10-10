package com.estebanmm13.pytra_api.metadata.service;

import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.metadata.client.SteamStoreClient;
import com.estebanmm13.pytra_api.metadata.dto.SteamGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.SteamStoreSearchResultDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Read-only lookups on the Steam store for the "autofill from Steam" action of the game form.
 * Store data is the same for every user, so results are cached globally.
 */
@Service
public class SteamStoreMetadataService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 100;
    static final int MAX_RESULTS = 10;

    private final SteamStoreClient steamStoreClient;
    private final TtlCache<String, List<SteamStoreSearchResultDto>> searchCache;
    private final TtlCache<Long, SteamGameMetadataDto> detailsCache;

    @Autowired
    public SteamStoreMetadataService(SteamStoreClient steamStoreClient) {
        this(steamStoreClient, Clock.systemUTC());
    }

    SteamStoreMetadataService(SteamStoreClient steamStoreClient, Clock clock) {
        this.steamStoreClient = steamStoreClient;
        this.searchCache = new TtlCache<>(200, Duration.ofMinutes(10), clock);
        this.detailsCache = new TtlCache<>(500, Duration.ofHours(6), clock);
    }

    public List<SteamStoreSearchResultDto> search(String query) {
        String term = query == null ? "" : query.strip();
        if (term.length() < MIN_QUERY_LENGTH || term.length() > MAX_QUERY_LENGTH) {
            throw new InvalidRequestException(InvalidRequestException.INVALID_SEARCH_QUERY);
        }
        return searchCache.get(term.toLowerCase(Locale.ROOT), () -> steamStoreClient.search(term, MAX_RESULTS));
    }

    public SteamGameMetadataDto getDetails(String appId) {
        long id = parseAppId(appId);
        return detailsCache.get(id, () -> steamStoreClient.getDetails(id));
    }

    /** Steam appids are positive integers; anything else is rejected before reaching Steam. */
    static long parseAppId(String appId) {
        if (appId == null || !appId.matches("\\d{1,10}")) {
            throw new InvalidRequestException(InvalidRequestException.INVALID_STEAM_APP_ID);
        }
        long id = Long.parseLong(appId);
        if (id <= 0) throw new InvalidRequestException(InvalidRequestException.INVALID_STEAM_APP_ID);
        return id;
    }
}
