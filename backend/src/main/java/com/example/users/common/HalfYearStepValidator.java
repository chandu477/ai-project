package com.example.users.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class HalfYearStepValidator implements ConstraintValidator<HalfYearStep, BigDecimal> {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        return value == null || value.multiply(TWO).stripTrailingZeros().scale() <= 0;
    }
}
