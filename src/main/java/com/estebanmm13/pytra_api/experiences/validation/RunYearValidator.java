package com.estebanmm13.pytra_api.experiences.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RunYearValidator implements ConstraintValidator<RunDate, Integer> {

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return RunDateLimits.isWithin(value);
    }
}
