package com.estebanmm13.pytra_api.auth.controller;

import com.estebanmm13.pytra_api.auth.dto.user.CurrentUserResponseDto;
import com.estebanmm13.pytra_api.auth.dto.user.UpdateProfileRequestDto;
import com.estebanmm13.pytra_api.auth.security.AuthenticatedUser;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUserResolver currentUserResolver;

    /** Shown instead of the real address to demo visitors (reserved domain, RFC 2606). */
    static final String DEMO_EMAIL_PLACEHOLDER = "demo@example.com";

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponseDto> getCurrentUser() {
        AuthenticatedUser currentUser = currentUserResolver.getCurrentUser();
        CurrentUserResponseDto response = userService.getCurrentUser(currentUser.getUserId());
        return ResponseEntity.ok(currentUser.isDemo() ? redactForDemo(response) : response);
    }

    @PatchMapping("/me")
    public ResponseEntity<CurrentUserResponseDto> updateProfile(
            @Valid @RequestBody UpdateProfileRequestDto updateProfileRequestDto) {
        return ResponseEntity.ok(userService.updateProfile(currentUserResolver.getCurrentUserId(), updateProfileRequestDto));
    }

    /** The demo account is public: never expose its owner's email. */
    private static CurrentUserResponseDto redactForDemo(CurrentUserResponseDto dto) {
        return new CurrentUserResponseDto(dto.getUsername(), dto.getUsernameDisplay(), DEMO_EMAIL_PLACEHOLDER,
                dto.isHasPassword(), dto.isGoogleLinked(), dto.getCreatedAt(), dto.getAvatar());
    }
}
