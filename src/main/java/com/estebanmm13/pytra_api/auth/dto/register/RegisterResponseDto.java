package com.estebanmm13.pytra_api.auth.dto.register;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RegisterResponseDto {

    private Long id;
    private String usernameDisplay;
    private String email;
}
