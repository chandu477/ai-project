package com.example.users.salary;

import com.example.users.common.HalfYearStep;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Input for a salary recommendation")
public record RecommendSalaryRequest(
        @Schema(description = "Years of professional experience of the new user, 0 to 60 in steps of 0.5", example = "2.5")
        @NotNull(message = "Years of experience is required")
        @DecimalMin(value = "0", message = "Years of experience must be between 0 and 60")
        @DecimalMax(value = "60", message = "Years of experience must be between 0 and 60")
        @HalfYearStep
        BigDecimal yearsOfExperience) {
}
