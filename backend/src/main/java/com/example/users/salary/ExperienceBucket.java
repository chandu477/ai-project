package com.example.users.salary;

import java.math.BigDecimal;

/** Salaries of users in one half-year band {@code [fromYears, fromYears + 0.5)}. Percentiles only. */
public record ExperienceBucket(BigDecimal fromYears, Distribution salaries) {
}
