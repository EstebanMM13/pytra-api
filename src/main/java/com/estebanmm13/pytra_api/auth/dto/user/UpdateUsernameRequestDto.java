package com.estebanmm13.pytra_api.auth.dto.user;

import com.estebanmm13.pytra_api.auth.validation.ValidUsername;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUsernameRequestDto {

    // Same rules as RegisterRequestDto.username
    @ValidUsername
    private String username;

    public void setUsername(String username) {
        this.username = username == null ? null : username.trim();
    }
}
