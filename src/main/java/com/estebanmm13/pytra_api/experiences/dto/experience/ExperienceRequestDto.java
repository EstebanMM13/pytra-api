package com.estebanmm13.pytra_api.experiences.dto.experience;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.Platform;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ExperienceRequestDto {

    @NotBlank
    private String runLabel;

    private Integer year;

    @NotNull
    private ExperienceStatus status;

    @Min(0)
    @Max(10)
    private Integer rating;

    @PositiveOrZero
    private Double hours;

    private LocalDate startDate;

    private LocalDate endDate;

    @NotNull
    private Platform platform;

    private Boolean platinum = false;

    private Boolean replay = false;

    private String summary;

    private String pros;

    private String cons;

    private String notes;
}
