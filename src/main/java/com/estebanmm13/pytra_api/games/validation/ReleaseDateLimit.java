package com.estebanmm13.pytra_api.games.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Release date sanity bound now that future dates are allowed (announced, unreleased games): at most
 * {@link ReleaseDateLimitValidator#MAX_YEARS_AHEAD} years from today. No lower bound, so games with old
 * dates always stay editable. A null value passes.
 */
@Documented
@Constraint(validatedBy = ReleaseDateLimitValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReleaseDateLimit {

    String message() default "must be at most " + ReleaseDateLimitValidator.MAX_YEARS_AHEAD + " years from today";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
