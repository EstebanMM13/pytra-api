package com.estebanmm13.pytra_api.experiences.dto.experience;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ExperienceRequestDto {

    @NotBlank
    private String runLabel;

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

    private LocalDate startDate;

    private LocalDate endDate;

    /** Catalog platform id. Required unless the legacy {@code platform} code is sent. */
    private Long platformId;

    /**
     * Legacy enum code (PC, PS5, PS4, XBOX, SWITCH, MOBILE) sent by clients built before the catalog
     * existed; only used when {@code platformId} is absent.
     */
    private String platform;

    private Boolean platinum = false;

    private Boolean replay = false;

    private String summary;

    private String pros;

    private String cons;

    private String notes;
}
