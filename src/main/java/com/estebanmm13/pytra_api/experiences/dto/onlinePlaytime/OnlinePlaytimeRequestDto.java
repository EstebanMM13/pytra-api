package com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class OnlinePlaytimeRequestDto {

    @NotNull
    @PositiveOrZero
    private Double totalHours;

    private LocalDateTime lastSessionAt;

    @Min(0)
    @Max(10)
    private Integer generalRating;

    private String notes;
}
