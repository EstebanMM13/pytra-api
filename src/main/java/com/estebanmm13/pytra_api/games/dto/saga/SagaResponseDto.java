package com.estebanmm13.pytra_api.games.dto.saga;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class SagaResponseDto {
    private Long id;
    private String name;
    private LocalDateTime updatedAt;
}
