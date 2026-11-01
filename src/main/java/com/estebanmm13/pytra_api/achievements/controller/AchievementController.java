package com.estebanmm13.pytra_api.achievements.controller;

import com.estebanmm13.pytra_api.achievements.dto.RecordAchievementsRequestDto;
import com.estebanmm13.pytra_api.achievements.dto.UnlockedAchievementDto;
import com.estebanmm13.pytra_api.achievements.service.AchievementService;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Achievements the user has unlocked; they stay unlocked once recorded. */
@RestController
@RequestMapping("/api/v1/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<List<UnlockedAchievementDto>> findUnlocked() {
        return ResponseEntity.ok(achievementService.findUnlocked(currentUserResolver.getCurrentUserId()));
    }

    /** Records newly unlocked ids (idempotent: an id already stored keeps its first date); answers the whole list. */
    @PostMapping
    public ResponseEntity<List<UnlockedAchievementDto>> record(@Valid @RequestBody RecordAchievementsRequestDto request) {
        return ResponseEntity.ok(achievementService.record(currentUserResolver.getCurrentUserId(), request.getIds()));
    }
}
