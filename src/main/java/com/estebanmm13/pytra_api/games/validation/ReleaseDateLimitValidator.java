package com.estebanmm13.pytra_api.games.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class ReleaseDateLimitValidator implements ConstraintValidator<ReleaseDateLimit, LocalDate> {

    /** How many years ahead an announced release date may be. */
    public static final int MAX_YEARS_AHEAD = 10;

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        return value == null || !value.isAfter(LocalDate.now().plusYears(MAX_YEARS_AHEAD));
    }
}
