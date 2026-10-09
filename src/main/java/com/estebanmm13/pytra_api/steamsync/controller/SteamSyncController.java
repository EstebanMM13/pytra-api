package com.estebanmm13.pytra_api.steamsync.controller;

import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.steamsync.dto.SteamSyncResultDto;
import com.estebanmm13.pytra_api.steamsync.service.SteamSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/integrations/steam")
@RequiredArgsConstructor
public class SteamSyncController {

    private final SteamSyncService steamSyncService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/sync")
    public ResponseEntity<SteamSyncResultDto> sync() {
        return ResponseEntity.ok(steamSyncService.sync(currentUserResolver.getCurrentUserId()));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<GameResponseDto>> getPending() {
        return ResponseEntity.ok(steamSyncService.getPending(currentUserResolver.getCurrentUserId()));
    }

    @PutMapping("/pending/{gameId}/confirm")
    public ResponseEntity<GameResponseDto> confirmPending(
            @PathVariable Long gameId,
            @Valid @RequestBody GameRequestDto gameRequestDto) {
        return ResponseEntity.ok(steamSyncService.confirmPending(gameId, gameRequestDto, currentUserResolver.getCurrentUserId()));
    }
}
