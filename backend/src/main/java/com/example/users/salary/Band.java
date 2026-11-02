package com.example.users.salary;

import java.math.BigDecimal;

/**
 * Users with experience in {@code [fromYears, toYears)}. The average is part of the statistical estimate;
 * it is sent to Claude for exactly one band (the target band).
 */
public record Band(BigDecimal fromYears, BigDecimal toYears, BigDecimal average, Distribution salaries) {

    Band roundedTo(int step) {
        return new Band(fromYears, toYears, Distribution.round(average, step), salaries.roundedTo(step));
    }
}
