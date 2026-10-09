package com.estebanmm13.pytra_api.auth.dto.register;

import com.estebanmm13.pytra_api.auth.validation.ValidUsername;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

@Getter
@Setter
public class RegisterRequestDto {

    @ValidUsername
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Length(min = 8)
    private String password;

    public void setUsername(String username) {
        this.username = username == null ? null : username.trim();
    }
}