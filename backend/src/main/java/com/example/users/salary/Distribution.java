package com.example.users.salary;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Count and salary percentiles of a group of users. No averages and no individual values. */
public record Distribution(long users, BigDecimal p10, BigDecimal p25, BigDecimal median,
                           BigDecimal p75, BigDecimal p90) {

    /** The same distribution with every salary rounded to the nearest {@code step} (HALF_UP). */
    Distribution roundedTo(int step) {
        return new Distribution(users, round(p10, step), round(p25, step), round(median, step),
                round(p75, step), round(p90, step));
    }

    static BigDecimal round(BigDecimal value, int step) {
        BigDecimal unit = BigDecimal.valueOf(step);
        return value.divide(unit, 0, RoundingMode.HALF_UP).multiply(unit);
    }
}
