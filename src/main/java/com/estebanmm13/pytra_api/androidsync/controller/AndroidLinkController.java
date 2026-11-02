package com.estebanmm13.pytra_api.androidsync.controller;

import com.estebanmm13.pytra_api.androidsync.dto.AndroidLinkRequestDto;
import com.estebanmm13.pytra_api.androidsync.dto.AndroidLinkResponseDto;
import com.estebanmm13.pytra_api.androidsync.service.AndroidLinkService;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Installed Android apps linked to games, whose daily usage the phone syncs as play time. */
@RestController
@RequestMapping("/api/v1/android-links")
@RequiredArgsConstructor
public class AndroidLinkController {

    private final AndroidLinkService androidLinkService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<List<AndroidLinkResponseDto>> findAll() {
        return ResponseEntity.ok(androidLinkService.findAll(currentUserResolver.getCurrentUserId()));
    }

    @PostMapping
    public ResponseEntity<AndroidLinkResponseDto> create(@Valid @RequestBody AndroidLinkRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(androidLinkService.create(currentUserResolver.getCurrentUserId(), request));
    }

    @DeleteMapping("/{packageName}")
    public ResponseEntity<Void> delete(@PathVariable String packageName) {
        androidLinkService.delete(currentUserResolver.getCurrentUserId(), packageName);
        return ResponseEntity.noContent().build();
    }
}
