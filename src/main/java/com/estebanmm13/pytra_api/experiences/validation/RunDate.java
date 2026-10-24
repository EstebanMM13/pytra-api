package com.estebanmm13.pytra_api.experiences.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A run date (or year) within {@link RunDateLimits}: from 1970-01-01 to tomorrow. A null value passes. */
@Documented
@Constraint(validatedBy = {RunDateValidator.class, RunYearValidator.class})
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface RunDate {

    String message() default "must be between 1970-01-01 and tomorrow";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
