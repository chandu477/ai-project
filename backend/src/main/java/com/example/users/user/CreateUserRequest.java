package com.example.users.user;

import com.example.users.common.HalfYearStep;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Data for a new user")
public record CreateUserRequest(
        @Schema(description = "Unique username: lowercase letters, digits, '.', '_' and '-'", example = "neha.k")
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be 3 to 50 characters")
        @Pattern(regexp = "[a-z0-9._-]+", message = "Use only lowercase letters, digits, '.', '_' and '-'")
        String username,

        @Schema(description = "Years of professional experience, 0 to 60 in steps of 0.5", example = "2.5")
        @NotNull(message = "Years of experience is required")
        @DecimalMin(value = "0", message = "Years of experience must be between 0 and 60")
        @DecimalMax(value = "60", message = "Years of experience must be between 0 and 60")
        @HalfYearStep
        BigDecimal yearsOfExperience,

        @Schema(description = "Salary per month", example = "82000.00")
        @NotNull(message = "Monthly salary is required")
        @DecimalMin(value = "0", inclusive = false, message = "Monthly salary must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "At most 10 digits and 2 decimals")
        BigDecimal monthlySalary) {
}
