package com.estebanmm13.pytra_api.platforms.dto;

import com.estebanmm13.pytra_api.platforms.model.PlatformFamily;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlatformRequestDto {

    /** Trimmed on binding, so the 1..40 limit applies to the trimmed name. */
    @NotBlank
    @Size(max = 40)
    private String name;

    /** Optional; defaults to OTHER. */
    private PlatformFamily family;

    /** Optional #RRGGBB; defaults to the family color. */
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    private String color;

    public void setName(String name) {
        this.name = name != null ? name.trim().replaceAll("\\s+", " ") : null;
    }
}
