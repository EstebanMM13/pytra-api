package com.estebanmm13.pytra_api.experiences.validation;

import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceRequestDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class RunDateRangeValidator implements ConstraintValidator<RunDateRange, ExperienceRequestDto> {

    static final String POSTGAME_MESSAGE = "must not be before the end date";

    @Override
    public boolean isValid(ExperienceRequestDto value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if (isBefore(value.getEndDate(), value.getStartDate())) {
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("endDate")
                    .addConstraintViolation();
            valid = false;
        }
        if (isBefore(value.getPostgameEndDate(), value.getEndDate())) {
            context.buildConstraintViolationWithTemplate(POSTGAME_MESSAGE)
                    .addPropertyNode("postgameEndDate")
                    .addConstraintViolation();
            valid = false;
        }
        return valid;
    }

    private static boolean isBefore(LocalDate date, LocalDate limit) {
        return date != null && limit != null && date.isBefore(limit);
    }
}
