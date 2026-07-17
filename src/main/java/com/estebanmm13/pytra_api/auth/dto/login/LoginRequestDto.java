package com.estebanmm13.pytra_api.auth.dto.login;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDto {

    @NotBlank
    private String identifier;

    @NotBlank
    private String password;
}