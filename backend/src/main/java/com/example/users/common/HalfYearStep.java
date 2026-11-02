package com.example.users.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** The number must be a multiple of 0.5 (0, 0.5, 1, ...). Null is valid; combine with @NotNull. */
@Documented
@Constraint(validatedBy = HalfYearStepValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface HalfYearStep {

    String message() default "Use steps of 0.5 years";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
