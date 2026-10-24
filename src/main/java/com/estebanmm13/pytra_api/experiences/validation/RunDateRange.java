package com.estebanmm13.pytra_api.experiences.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The end date of a run is not before its start date (either may be missing). Reported on the
 * {@code endDate} field so it shows up in {@code fieldErrors} like any other field error.
 */
@Documented
@Constraint(validatedBy = RunDateRangeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RunDateRange {

    String message() default "must not be before the start date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
