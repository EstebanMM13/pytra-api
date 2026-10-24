package com.estebanmm13.pytra_api.obsidian.controller;

import com.estebanmm13.pytra_api.auth.security.AuthenticatedUser;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.auth.security.PersonalAccessTokenPrincipal;
import com.estebanmm13.pytra_api.obsidian.dto.ObsidianExportDto;
import com.estebanmm13.pytra_api.obsidian.service.ObsidianExportRateLimiter;
import com.estebanmm13.pytra_api.obsidian.service.ObsidianExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Snapshot for the Obsidian sync plugin. The only endpoint that accepts a personal access token
 * (see PersonalAccessTokenFilter); a session JWT works too, which is handy for testing.
 */
@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
public class ObsidianExportController {

    private final ObsidianExportService exportService;
    private final ObsidianExportRateLimiter rateLimiter;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/obsidian")
    public ObsidianExportDto exportObsidian() {
        AuthenticatedUser currentUser = currentUserResolver.getCurrentUser();
        rateLimiter.acquire(currentUser instanceof PersonalAccessTokenPrincipal token
                ? "token:" + token.getTokenId()
                : "user:" + currentUser.getUserId());
        return exportService.export(currentUser.getUserId());
    }
}
