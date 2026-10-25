package com.estebanmm13.pytra_api.experiences.dto.experience;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.validation.RunDate;
import com.estebanmm13.pytra_api.experiences.validation.RunDateRange;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@RunDateRange
public class ExperienceRequestDto {

    @NotBlank
    private String runLabel;

    @RunDate
    private Integer year;

    @NotNull
    private ExperienceStatus status;

    /** 0..10 with up to 2 decimals; more decimals are rejected (400), never rounded. */
    @DecimalMin("0")
    @DecimalMax("10")
    @Digits(integer = 2, fraction = 2)
    private BigDecimal rating;

    @PositiveOrZero
    private Double hours;

    @RunDate
    private LocalDate startDate;

    @RunDate
    private LocalDate endDate;

    /** Catalog platform id. Required unless the legacy {@code platform} code is sent. */
    private Long platformId;

    /**
     * Legacy enum code (PC, PS5, PS4, XBOX, SWITCH, MOBILE) sent by clients built before the catalog
     * existed; only used when {@code platformId} is absent.
     */
    private String platform;

    private Boolean platinum = false;

    /** Optional for clients built before the flag existed; absent means false. */
    private Boolean fullCompletion = false;

    private Boolean replay = false;

    private String summary;

    private String pros;

    private String cons;

    private String notes;
}
