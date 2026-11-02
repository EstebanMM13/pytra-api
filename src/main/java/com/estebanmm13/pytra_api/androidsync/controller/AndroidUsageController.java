package com.estebanmm13.pytra_api.androidsync.controller;

import com.estebanmm13.pytra_api.androidsync.dto.AndroidUsageDayDto;
import com.estebanmm13.pytra_api.androidsync.dto.AndroidUsageSyncResponseDto;
import com.estebanmm13.pytra_api.androidsync.service.AndroidUsageSyncService;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Daily foreground minutes of Android apps, sent by the phone (the day's whole total, re-sent every sync). */
@RestController
@RequestMapping("/api/v1/android-usage")
@RequiredArgsConstructor
public class AndroidUsageController {

    private final AndroidUsageSyncService androidUsageSyncService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/sync")
    public ResponseEntity<AndroidUsageSyncResponseDto> sync(@RequestBody List<AndroidUsageDayDto> days) {
        return ResponseEntity.ok(androidUsageSyncService.sync(currentUserResolver.getCurrentUserId(), days));
    }
}
