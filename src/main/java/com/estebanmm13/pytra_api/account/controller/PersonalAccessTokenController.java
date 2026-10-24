package com.estebanmm13.pytra_api.account.controller;

import com.estebanmm13.pytra_api.account.dto.AccessTokenResponseDto;
import com.estebanmm13.pytra_api.account.dto.CreateAccessTokenRequestDto;
import com.estebanmm13.pytra_api.account.dto.CreatedAccessTokenResponseDto;
import com.estebanmm13.pytra_api.account.service.PersonalAccessTokenService;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Personal access tokens of the current user (for the Obsidian sync). Managed with the regular session
 * JWT only: a personal access token is never accepted here (see PersonalAccessTokenFilter).
 */
@RestController
@RequestMapping("/api/v1/users/me/tokens")
@RequiredArgsConstructor
public class PersonalAccessTokenController {

    private final PersonalAccessTokenService tokenService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    public ResponseEntity<CreatedAccessTokenResponseDto> create(@Valid @RequestBody CreateAccessTokenRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tokenService.create(currentUserResolver.getCurrentUserId(), requestDto.getName()));
    }

    @GetMapping
    public List<AccessTokenResponseDto> list() {
        return tokenService.list(currentUserResolver.getCurrentUserId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        tokenService.revoke(currentUserResolver.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
