package com.example.users.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "A user row in the listing")
public record UserSummary(
        @Schema(description = "User ID", example = "42", accessMode = Schema.AccessMode.READ_ONLY)
        Long id,

        @Schema(description = "Unique username", example = "alice.0042")
        String username,

        @Schema(description = "Number of orders placed by the user", example = "7")
        long orderCount,

        @Schema(description = "Salary per month", example = "55000.00")
        BigDecimal monthlySalary,

        @Schema(description = "Years of professional experience", example = "8")
        int yearsOfExperience) {
}
