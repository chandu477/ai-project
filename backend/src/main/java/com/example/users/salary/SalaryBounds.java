package com.example.users.salary;

import java.math.BigDecimal;

/** Lowest and highest salary of all users. Used only to validate AI answers; never sent to Claude. */
public record SalaryBounds(BigDecimal min, BigDecimal max) {
}
