package com.estebanmm13.pytra_api.metadata.controller;

import com.estebanmm13.pytra_api.metadata.dto.SteamGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.SteamStoreSearchResultDto;
import com.estebanmm13.pytra_api.metadata.service.SteamStoreMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only proxy to the Steam store used to prefill the game form (the browser can't call the
 * store directly: no CORS). Nothing is persisted here.
 *
 * <p>Errors use {@code ApiError.message} as a stable code: 400 INVALID_SEARCH_QUERY /
 * INVALID_STEAM_APP_ID, 404 STEAM_APP_NOT_FOUND, 422 STEAM_APP_NOT_A_GAME, 503 STEAM_UNAVAILABLE.
 */
@RestController
@RequestMapping("/api/v1/metadata/steam")
@RequiredArgsConstructor
public class GameMetadataController {

    private final SteamStoreMetadataService steamStoreMetadataService;

    @GetMapping("/search")
    public ResponseEntity<List<SteamStoreSearchResultDto>> search(@RequestParam(name = "q", required = false) String query) {
        return ResponseEntity.ok(steamStoreMetadataService.search(query));
    }

    @GetMapping("/{appId}")
    public ResponseEntity<SteamGameMetadataDto> getDetails(@PathVariable String appId) {
        return ResponseEntity.ok(steamStoreMetadataService.getDetails(appId));
    }
}
