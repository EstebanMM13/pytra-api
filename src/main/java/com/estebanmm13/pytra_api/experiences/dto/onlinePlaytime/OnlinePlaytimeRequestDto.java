package com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class OnlinePlaytimeRequestDto {

    @NotNull
    @PositiveOrZero
    private Double totalHours;

    private LocalDateTime lastSessionAt;

    /** 0..10 with up to 2 decimals; more decimals are rejected (400), never rounded. */
    @DecimalMin("0")
    @DecimalMax("10")
    @Digits(integer = 2, fraction = 2)
    private BigDecimal generalRating;

    private String notes;

    /**
     * Null keeps the stored flag (false on create): unlike a run's, this PUT is also sent by clients built
     * before the flag existed, and those must not wipe a platinum the user set from a newer one.
     */
    private Boolean platinum;

    /** "100%" flag; null keeps the stored value (false on create), like {@link #platinum}. */
    private Boolean fullCompletion;

    /**
     * Catalog platform ids played online on. Null keeps the stored platforms (clients that predate the
     * field); an empty set clears them. Unknown ids are rejected with INVALID_PLATFORM.
     */
    @Size(max = 20)
    private Set<@NotNull Long> platformIds;

    /**
     * Online hours per platform id, a breakdown of {@link #totalHours}. Null keeps the stored breakdown;
     * an empty map clears it. Every id must be one of the record's platforms (INVALID_PLATFORM) and the
     * hours cannot add up to more than {@link #totalHours} (PLATFORM_HOURS_EXCEED_TOTAL). Ignored, and the
     * breakdown cleared, while the record has fewer than two platforms.
     */
    @Size(max = 20)
    private Map<@NotNull Long, @NotNull @PositiveOrZero Double> platformHours;
}
