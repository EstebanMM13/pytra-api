package com.estebanmm13.pytra_api.games.dto.saga;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SagaRequestDto {

    @NotBlank
    private String name;
}
