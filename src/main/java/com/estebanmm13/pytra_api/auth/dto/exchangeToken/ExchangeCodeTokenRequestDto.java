package com.estebanmm13.pytra_api.auth.dto.exchangeToken;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExchangeCodeTokenRequestDto {

    @NotBlank
    private String code;

}
