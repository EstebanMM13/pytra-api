package com.estebanmm13.pytra_api.experiences.controller;

import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceResponseDto;
import com.estebanmm13.pytra_api.experiences.service.experienceService.ExperienceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/games/{gameId}/experiences")
@RequiredArgsConstructor
public class GameExperienceController {

    private final ExperienceService experienceService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping()
    public ResponseEntity<List<ExperienceResponseDto>> findAllByGame(@PathVariable Long gameId) {
        return ResponseEntity.ok(experienceService.findAllByGame(gameId, currentUserResolver.getCurrentUserId()));
    }

    @PostMapping()
    public ResponseEntity<ExperienceResponseDto> createExperience(
            @PathVariable Long gameId,
            @Valid @RequestBody ExperienceRequestDto experienceRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(experienceService.create(gameId, experienceRequestDto, currentUserResolver.getCurrentUserId()));
    }
}
