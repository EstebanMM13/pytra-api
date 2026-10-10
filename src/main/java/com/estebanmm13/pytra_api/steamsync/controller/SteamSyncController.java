package com.estebanmm13.pytra_api.steamsync.controller;

import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.steamsync.dto.SteamIgnoredAppDto;
import com.estebanmm13.pytra_api.steamsync.dto.SteamPendingGameDto;
import com.estebanmm13.pytra_api.steamsync.dto.SteamStatusDto;
import com.estebanmm13.pytra_api.steamsync.dto.SteamSyncResultDto;
import com.estebanmm13.pytra_api.steamsync.service.SteamLinkService;
import com.estebanmm13.pytra_api.steamsync.service.SteamSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Errors use {@code ApiError.message} as a stable code: 503 STEAM_NOT_CONFIGURED,
 * 404 STEAM_NOT_LINKED, 409 SYNC_IN_PROGRESS / STEAM_LINK_CHANGED, 429 STEAM_RATE_LIMITED,
 * 502 STEAM_API_KEY_REJECTED, 503 STEAM_UNAVAILABLE.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/integrations/steam")
@RequiredArgsConstructor
public class SteamSyncController {

    private final SteamSyncService steamSyncService;
    private final SteamLinkService steamLinkService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/status")
    public ResponseEntity<SteamStatusDto> status() {
        return ResponseEntity.ok(steamLinkService.getStatus(currentUserResolver.getCurrentUserId()));
    }

    @DeleteMapping("/link")
    public ResponseEntity<Void> unlink() {
        steamLinkService.unlink(currentUserResolver.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync")
    public ResponseEntity<SteamSyncResultDto> sync() {
        return ResponseEntity.ok(steamSyncService.sync(currentUserResolver.getCurrentUserId()));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<SteamPendingGameDto>> getPending() {
        return ResponseEntity.ok(steamSyncService.getPending(currentUserResolver.getCurrentUserId()));
    }

    @PutMapping("/pending/{gameId}/confirm")
    public ResponseEntity<GameResponseDto> confirmPending(
            @PathVariable Long gameId,
            @Valid @RequestBody GameRequestDto gameRequestDto) {
        return ResponseEntity.ok(steamSyncService.confirmPending(gameId, gameRequestDto, currentUserResolver.getCurrentUserId()));
    }

    @PutMapping("/pending/{gameId}/ignore")
    public ResponseEntity<Void> ignorePending(@PathVariable Long gameId) {
        steamSyncService.ignorePending(gameId, currentUserResolver.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ignored")
    public ResponseEntity<List<SteamIgnoredAppDto>> getIgnored() {
        return ResponseEntity.ok(steamSyncService.getIgnored(currentUserResolver.getCurrentUserId()));
    }

    @DeleteMapping("/ignored/{appId}")
    public ResponseEntity<Void> unignore(@PathVariable String appId) {
        steamSyncService.unignore(appId, currentUserResolver.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
