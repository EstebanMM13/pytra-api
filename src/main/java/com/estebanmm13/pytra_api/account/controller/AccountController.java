package com.estebanmm13.pytra_api.account.controller;

import com.estebanmm13.pytra_api.account.dto.DeleteAccountRequestDto;
import com.estebanmm13.pytra_api.account.export.ExportFile;
import com.estebanmm13.pytra_api.account.service.AccountService;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Account-wide operations on the current user (export, deletion); the profile lives in UserController. */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String format) {
        ExportFile file = accountService.export(currentUserResolver.getCurrentUserId(), format);
        return ResponseEntity.ok()
                .contentType(file.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename())
                        .build()
                        .toString())
                .body(file.content());
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequestDto requestDto) {
        accountService.deleteAccount(currentUserResolver.getCurrentUserId(), requestDto.getConfirm());
        return ResponseEntity.noContent().build();
    }
}
