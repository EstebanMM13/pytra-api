package com.estebanmm13.pytra_api.stats.controller;

import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.stats.dto.*;
import com.estebanmm13.pytra_api.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/summary")
    public ResponseEntity<StatsSummaryDto> getSummary() {
        return ResponseEntity.ok(statsService.getSummary(currentUserResolver.getCurrentUserId()));
    }

    @GetMapping("/by-year")
    public ResponseEntity<List<YearStatDto>> getByYear() {
        return ResponseEntity.ok(statsService.getByYear(currentUserResolver.getCurrentUserId()));
    }

    @GetMapping("/by-saga")
    public ResponseEntity<List<SagaStatDto>> getBySaga() {
        return ResponseEntity.ok(statsService.getBySaga(currentUserResolver.getCurrentUserId()));
    }

    @GetMapping("/by-genre")
    public ResponseEntity<List<GenreStatDto>> getByGenre() {
        return ResponseEntity.ok(statsService.getByGenre(currentUserResolver.getCurrentUserId()));
    }

    @GetMapping("/top-rated")
    public ResponseEntity<List<TopRatedExperienceDto>> getTopRated(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(statsService.getTopRated(currentUserResolver.getCurrentUserId(), limit));
    }

    @GetMapping("/most-played/singleplayer")
    public ResponseEntity<List<MostPlayedGameDto>> getMostPlayedSingleplayer(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(statsService.getMostPlayedSingleplayer(currentUserResolver.getCurrentUserId(), limit));
    }

    @GetMapping("/most-played/online")
    public ResponseEntity<List<MostPlayedGameDto>> getMostPlayedOnline(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(statsService.getMostPlayedOnline(currentUserResolver.getCurrentUserId(), limit));
    }
}
