package com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime;

import com.estebanmm13.pytra_api.platforms.dto.PlatformRefDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    /** Catalog platforms played online on, in catalog order (empty when none). */
    private List<PlatformRefDto> platforms;
}
