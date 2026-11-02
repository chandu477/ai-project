package com.example.users.salary;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** The API key is deliberately not part of this record; it is read from ANTHROPIC_API_KEY. */
@Validated
@ConfigurationProperties("salary-advisor")
public record SalaryAdvisorProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("claude-opus-5-5") @NotBlank String model,
        @DefaultValue("low") @Pattern(regexp = "low|medium|high") String effort,
        @DefaultValue("8s") @NotNull Duration timeout,
        @DefaultValue("1") @Min(0) int maxRetries,
        @DefaultValue("4") @Min(1) int maxConcurrentCalls) {
}
