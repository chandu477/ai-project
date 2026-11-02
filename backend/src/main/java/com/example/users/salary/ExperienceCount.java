package com.example.users.salary;

import java.math.BigDecimal;

/** Number of users with exactly this experience. */
public interface ExperienceCount {

    BigDecimal getYears();

    Long getUsers();
}
