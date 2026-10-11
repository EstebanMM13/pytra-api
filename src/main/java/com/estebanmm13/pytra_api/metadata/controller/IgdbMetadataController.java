package com.estebanmm13.pytra_api.metadata.controller;

import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.IgdbSearchResultDto;
import com.estebanmm13.pytra_api.metadata.dto.MetadataProvidersDto;
import com.estebanmm13.pytra_api.metadata.service.IgdbMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only proxy to IGDB used to prefill the game form (the browser can't hold the Twitch
 * credentials). Nothing is persisted here.
 *
 * <p>Errors use {@code ApiError.message} as a stable code: 400 INVALID_SEARCH_QUERY / INVALID_IGDB_ID,
 * 404 IGDB_DISABLED (no Twitch credentials configured), 404 IGDB_GAME_NOT_FOUND, 503 IGDB_UNAVAILABLE.
 */
@RestController
@RequestMapping("/api/v1/metadata")
@RequiredArgsConstructor
public class IgdbMetadataController {

    private final IgdbMetadataService igdbMetadataService;

    /** Which autofill sources are available, so clients can hide IGDB when it is disabled. */
    @GetMapping("/providers")
    public ResponseEntity<MetadataProvidersDto> providers() {
        return ResponseEntity.ok(igdbMetadataService.providers());
    }

    @GetMapping("/igdb/search")
    public ResponseEntity<List<IgdbSearchResultDto>> search(@RequestParam(name = "q", required = false) String query) {
        return ResponseEntity.ok(igdbMetadataService.search(query));
    }

    @GetMapping("/igdb/{igdbId}")
    public ResponseEntity<IgdbGameMetadataDto> getDetails(@PathVariable String igdbId) {
        return ResponseEntity.ok(igdbMetadataService.getDetails(igdbId));
    }
}
