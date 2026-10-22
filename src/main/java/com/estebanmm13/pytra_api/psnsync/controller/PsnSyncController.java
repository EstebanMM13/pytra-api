package com.estebanmm13.pytra_api.psnsync.controller;

import com.estebanmm13.pytra_api.auth.security.AuthenticatedUser;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.psnsync.dto.PsnIgnoredTitleDto;
import com.estebanmm13.pytra_api.psnsync.dto.PsnLinkRequestDto;
import com.estebanmm13.pytra_api.psnsync.dto.PsnPendingGameDto;
import com.estebanmm13.pytra_api.psnsync.dto.PsnStatusDto;
import com.estebanmm13.pytra_api.psnsync.dto.PsnSyncResultDto;
import com.estebanmm13.pytra_api.psnsync.service.PsnLinkService;
import com.estebanmm13.pytra_api.psnsync.service.PsnSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PlayStation Network sync. Users only type their online ID; the public game list is read with
 * Pytra's service account. Mirrors the Steam endpoints.
 *
 * <p>Errors use {@code ApiError.message} as a stable code: 400 PSN_INVALID_ONLINE_ID,
 * 404 PSN_USER_NOT_FOUND, 422 PSN_PROFILE_PRIVATE (link only), 503 PSN_NOT_CONFIGURED, 404 PSN_NOT_LINKED, 409 SYNC_IN_PROGRESS /
 * PSN_LINK_CHANGED, 429 PSN_RATE_LIMITED, 503 PSN_UNAVAILABLE (also when the service account is
 * rejected; admins see the reason in the status).
 */
@RestController
@RequestMapping("/api/v1/integrations/psn")
@RequiredArgsConstructor
public class PsnSyncController {

    private final PsnSyncService psnSyncService;
    private final PsnLinkService psnLinkService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/status")
    public ResponseEntity<PsnStatusDto> status() {
        AuthenticatedUser currentUser = currentUserResolver.getCurrentUser();
        PsnStatusDto status = psnLinkService.getStatus(currentUser.getUserId());
        return ResponseEntity.ok(redact(status, currentUser.isDemo(), currentUserResolver.isAdmin()));
    }

    /** Links (or re-links) the PSN account; answers the new status. */
    @PutMapping("/link")
    public ResponseEntity<PsnStatusDto> link(@RequestBody PsnLinkRequestDto request) {
        AuthenticatedUser currentUser = currentUserResolver.getCurrentUser();
        PsnStatusDto status = psnLinkService.link(currentUser.getUserId(), request.onlineId());
        return ResponseEntity.ok(redact(status, currentUser.isDemo(), currentUserResolver.isAdmin()));
    }

    /**
     * The demo account is public: hide which PSN account is linked to it. The service account's
     * health is for admins only (it tells them to renew PSN_NPSSO).
     */
    static PsnStatusDto redact(PsnStatusDto status, boolean demo, boolean admin) {
        return new PsnStatusDto(status.linked(),
                demo ? null : status.accountId(),
                demo ? null : status.onlineId(),
                status.lastSyncAt(), status.configured(),
                status.linkedGamesCount(), status.pendingCount(), status.ignoredCount(),
                admin ? status.serviceAuthError() : null,
                admin ? status.serviceAuthFailingSince() : null,
                status.enrichment());
    }

    @DeleteMapping("/link")
    public ResponseEntity<Void> unlink() {
        psnLinkService.unlink(currentUserResolver.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync")
    public ResponseEntity<PsnSyncResultDto> sync() {
        return ResponseEntity.ok(psnSyncService.sync(currentUserResolver.getCurrentUserId()));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<PsnPendingGameDto>> getPending() {
        return ResponseEntity.ok(psnSyncService.getPending(currentUserResolver.getCurrentUserId()));
    }

    @PutMapping("/pending/{gameId}/confirm")
    public ResponseEntity<GameResponseDto> confirmPending(
            @PathVariable Long gameId,
            @Valid @RequestBody GameRequestDto gameRequestDto) {
        return ResponseEntity.ok(psnSyncService.confirmPending(gameId, gameRequestDto, currentUserResolver.getCurrentUserId()));
    }

    @PutMapping("/pending/{gameId}/ignore")
    public ResponseEntity<Void> ignorePending(@PathVariable Long gameId) {
        psnSyncService.ignorePending(gameId, currentUserResolver.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ignored")
    public ResponseEntity<List<PsnIgnoredTitleDto>> getIgnored() {
        return ResponseEntity.ok(psnSyncService.getIgnored(currentUserResolver.getCurrentUserId()));
    }

    @DeleteMapping("/ignored/{titleId}")
    public ResponseEntity<Void> unignore(@PathVariable String titleId) {
        psnSyncService.unignore(titleId, currentUserResolver.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
