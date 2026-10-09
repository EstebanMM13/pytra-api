package com.estebanmm13.pytra_api.auth.dto.resendVerification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResendVerificationRequestDto {

    @NotBlank
    @Email
    private String email;
}
