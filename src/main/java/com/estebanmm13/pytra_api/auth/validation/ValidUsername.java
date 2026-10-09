package com.estebanmm13.pytra_api.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Username rules shared by registration and profile updates: 3-30 characters, only ASCII letters, digits,
 * '.', '_' and '-' (so never '@', spaces, control or other unicode characters). DTO setters trim the value
 * before validation.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@NotBlank
@Size(min = UsernamePolicy.MIN_LENGTH, max = UsernamePolicy.MAX_LENGTH)
@Pattern(regexp = UsernamePolicy.INPUT_REGEX,
        message = "must be 3-30 characters: letters, digits, '.', '_' or '-'")
public @interface ValidUsername {

    String message() default "invalid username";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
