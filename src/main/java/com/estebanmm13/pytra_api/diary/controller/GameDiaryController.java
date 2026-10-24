package com.estebanmm13.pytra_api.diary.controller;

import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.diary.dto.GameDiaryDayResponseDto;
import com.estebanmm13.pytra_api.diary.service.DiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The days one game was played, latest first (the game detail's "days played"). */
@RestController
@RequestMapping("/api/v1/games/{gameId}/diary")
@RequiredArgsConstructor
public class GameDiaryController {

    private final DiaryService diaryService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<List<GameDiaryDayResponseDto>> findByGame(@PathVariable Long gameId) {
        return ResponseEntity.ok(diaryService.findByGame(gameId, currentUserResolver.getCurrentUserId()));
    }
}
