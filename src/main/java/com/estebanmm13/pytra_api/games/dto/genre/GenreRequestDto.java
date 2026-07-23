package com.estebanmm13.pytra_api.games.dto.genre;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenreRequestDto {

    @NotBlank
    private String name;
}
