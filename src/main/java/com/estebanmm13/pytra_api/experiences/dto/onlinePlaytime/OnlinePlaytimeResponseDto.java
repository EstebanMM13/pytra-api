package com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OnlinePlaytimeResponseDto {
    private Long id;
    private Long gameId;
    private Double totalHours;
    private LocalDateTime lastSessionAt;
    private BigDecimal generalRating;
    private String notes;
    private LocalDateTime updatedAt;
}
