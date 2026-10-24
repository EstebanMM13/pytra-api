package com.estebanmm13.pytra_api.experiences.validation;

import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceRequestDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RunDateRangeValidator implements ConstraintValidator<RunDateRange, ExperienceRequestDto> {

    @Override
    public boolean isValid(ExperienceRequestDto value, ConstraintValidatorContext context) {
        if (value == null || value.getStartDate() == null || value.getEndDate() == null
                || !value.getEndDate().isBefore(value.getStartDate())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("endDate")
                .addConstraintViolation();
        return false;
    }
}
