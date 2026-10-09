package com.estebanmm13.pytra_api.experiences.controller;

import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeResponseDto;
import com.estebanmm13.pytra_api.experiences.service.onlinePlaytimeService.OnlinePlaytimeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/games/{gameId}/online-playtime")
@RequiredArgsConstructor
public class GameOnlinePlaytimeController {

    private final OnlinePlaytimeService onlinePlaytimeService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping()
    public ResponseEntity<OnlinePlaytimeResponseDto> findByGame(@PathVariable Long gameId) {
        return ResponseEntity.ok(onlinePlaytimeService.findByGame(gameId, currentUserResolver.getCurrentUserId()));
    }

    @PutMapping()
    public ResponseEntity<OnlinePlaytimeResponseDto> upsert(
            @PathVariable Long gameId,
            @Valid @RequestBody OnlinePlaytimeRequestDto onlinePlaytimeRequestDto) {
        return ResponseEntity.ok(onlinePlaytimeService.upsert(gameId, onlinePlaytimeRequestDto, currentUserResolver.getCurrentUserId()));
    }
}
