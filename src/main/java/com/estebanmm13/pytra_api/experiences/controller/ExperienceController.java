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

@Slf4j
@RestController
@RequestMapping("/api/v1/experiences")
@RequiredArgsConstructor
public class ExperienceController {

    private final ExperienceService experienceService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/{id}")
    public ResponseEntity<ExperienceResponseDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(experienceService.findById(id, currentUserResolver.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExperienceResponseDto> updateExperience(
            @PathVariable Long id,
            @Valid @RequestBody ExperienceRequestDto experienceRequestDto) {
        return ResponseEntity.ok(experienceService.update(id, experienceRequestDto, currentUserResolver.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExperience(@PathVariable Long id) {
        experienceService.delete(id, currentUserResolver.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
