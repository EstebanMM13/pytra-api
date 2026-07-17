package com.estebanmm13.pytra_api.auth.dto.resetPassword;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

@Getter
@Setter
public class ResetPasswordRequestDto {

    @NotBlank
    private String token;

    @NotBlank
    @Length(min = 8)
    private String newPassword;
}
