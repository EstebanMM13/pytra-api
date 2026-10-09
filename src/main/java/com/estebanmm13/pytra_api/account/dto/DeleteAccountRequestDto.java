package com.estebanmm13.pytra_api.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteAccountRequestDto {

    /** Must repeat the caller's username (case-insensitive) to confirm the deletion. */
    @NotBlank
    private String confirm;
}
