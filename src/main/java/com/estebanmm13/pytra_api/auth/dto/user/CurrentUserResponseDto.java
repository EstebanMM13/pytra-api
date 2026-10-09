package com.estebanmm13.pytra_api.auth.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CurrentUserResponseDto {

    private String username;
    private String usernameDisplay;
    private String email;
    private boolean hasPassword;
    private boolean googleLinked;
    /** Account creation time ("member since"). */
    private LocalDateTime createdAt;
}
