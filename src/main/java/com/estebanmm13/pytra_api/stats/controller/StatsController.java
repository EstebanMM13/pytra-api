package com.estebanmm13.pytra_api.stats.controller;

import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.stats.dto.*;
import com.estebanmm13.pytra_api.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** Years that have at least one experience, newest first (drives the year tabs). */
    @GetMapping("/years")
    public ResponseEntity<List<Integer>> getYears() {
        return ResponseEntity.ok(statsService.getYears(currentUserResolver.getCurrentUserId()));
    }

    /** Year in review; a year without data answers 200 with zeros and empty lists. */
    @GetMapping("/years/{year}")
    public ResponseEntity<YearSummaryDto> getYearSummary(@PathVariable int year) {
        return ResponseEntity.ok(statsService.getYearSummary(currentUserResolver.getCurrentUserId(), year));
    }

    /** Creates or replaces the year's free-text note; blank texts are stored as null. */
    @PutMapping("/years/{year}/note")
    public ResponseEntity<YearNoteDto> upsertYearNote(
            @PathVariable int year,
            @Valid @RequestBody YearNoteRequestDto yearNoteRequestDto) {
        return ResponseEntity.ok(statsService.upsertYearNote(currentUserResolver.getCurrentUserId(), year, yearNoteRequestDto));
    }

    /** EN_CURSO experiences across all games. */
    @GetMapping("/in-progress")
    public ResponseEntity<List<InProgressExperienceDto>> getInProgress() {
        return ResponseEntity.ok(statsService.getInProgress(currentUserResolver.getCurrentUserId()));
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
