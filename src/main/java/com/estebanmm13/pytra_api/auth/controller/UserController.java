package com.estebanmm13.pytra_api.auth.controller;

import com.estebanmm13.pytra_api.auth.dto.user.CurrentUserResponseDto;
import com.estebanmm13.pytra_api.auth.dto.user.UpdateProfileRequestDto;
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

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponseDto> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUser(currentUserResolver.getCurrentUserId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<CurrentUserResponseDto> updateProfile(
            @Valid @RequestBody UpdateProfileRequestDto updateProfileRequestDto) {
        return ResponseEntity.ok(userService.updateProfile(currentUserResolver.getCurrentUserId(), updateProfileRequestDto));
    }
}
