package com.example.users.salary;

import java.math.BigDecimal;

/** Result row of the SQL salary aggregates. Values are null when the group is empty. */
public interface SalaryAggregate {

    Long getUsers();

    BigDecimal getMinSalary();

    BigDecimal getMaxSalary();

    BigDecimal getAverage();

    BigDecimal getP10();

    BigDecimal getP25();

    BigDecimal getMedian();

    BigDecimal getP75();

    BigDecimal getP90();
}
