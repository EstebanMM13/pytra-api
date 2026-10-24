package com.estebanmm13.pytra_api.experiences.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class RunDateValidator implements ConstraintValidator<RunDate, LocalDate> {

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        return RunDateLimits.isWithin(value);
    }
}
