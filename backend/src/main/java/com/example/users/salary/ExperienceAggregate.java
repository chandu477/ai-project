package com.example.users.salary;

import java.math.BigDecimal;

/** Aggregates of one half-year experience value. */
public interface ExperienceAggregate extends SalaryAggregate {

    BigDecimal getYears();
}
