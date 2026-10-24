package com.estebanmm13.pytra_api.diary.controller;

import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.diary.dto.DiaryDayRequestDto;
import com.estebanmm13.pytra_api.diary.dto.DiaryDayResponseDto;
import com.estebanmm13.pytra_api.diary.service.DiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** The play diary: one day per date (yyyy-MM-dd) with the games played and their hours. */
@RestController
@RequestMapping("/api/v1/diary")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<List<DiaryDayResponseDto>> findRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(diaryService.findRange(from, to, currentUserResolver.getCurrentUserId()));
    }

    @GetMapping("/{date}")
    public ResponseEntity<DiaryDayResponseDto> findDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(diaryService.findDay(date, currentUserResolver.getCurrentUserId()));
    }

    /** Replaces the whole day. A day sent with no games and no note is deleted (204). */
    @PutMapping("/{date}")
    public ResponseEntity<DiaryDayResponseDto> saveDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody DiaryDayRequestDto request) {
        return diaryService.saveDay(date, request, currentUserResolver.getCurrentUserId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> deleteDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        diaryService.deleteDay(date, currentUserResolver.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
