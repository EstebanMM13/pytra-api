package com.estebanmm13.pytra_api.diary.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A game played on the day. {@code experienceId} picks the run the hours go to; it may be omitted when
 * the game has exactly one run in progress (or is ONLINE / platform-synced).
 */
@Getter
@Setter
public class DiaryGameRequestDto {

    @NotNull
    private Long gameId;

    private Long experienceId;

    /** More than 0 and at most 24, with up to 2 decimals; more decimals are rejected, never rounded. */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @DecimalMax("24")
    @Digits(integer = 2, fraction = 2)
    private BigDecimal hours;

    @Size(max = 500)
    private String note;
}
