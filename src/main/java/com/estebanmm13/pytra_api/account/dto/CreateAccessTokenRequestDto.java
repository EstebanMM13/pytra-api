package com.estebanmm13.pytra_api.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAccessTokenRequestDto {

    /** Label the user picks to recognize the token (e.g. "Obsidian"); trimmed before saving. */
    @NotBlank
    @Size(max = 50)
    private String name;
}
