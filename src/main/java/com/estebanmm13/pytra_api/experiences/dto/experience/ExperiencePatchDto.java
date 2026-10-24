package com.estebanmm13.pytra_api.experiences.dto.experience;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Partial update of a run's figures (the inline edit of the run detail page). Same rules as
 * {@link ExperienceRequestDto}. A missing or null {@code hours} keeps the stored hours; {@code rating}
 * is changed only when the field is sent, so {@code "rating": null} clears it.
 */
@Getter
public class ExperiencePatchDto {

    @PositiveOrZero
    private Double hours;

    /** 0..10 with up to 2 decimals; more decimals are rejected (400), never rounded. */
    @DecimalMin("0")
    @DecimalMax("10")
    @Digits(integer = 2, fraction = 2)
    private BigDecimal rating;

    @JsonIgnore
    private boolean ratingSent;

    public void setHours(Double hours) {
        this.hours = hours;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
        this.ratingSent = true;
    }
}
