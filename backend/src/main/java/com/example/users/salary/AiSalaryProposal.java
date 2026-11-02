package com.example.users.salary;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Structured output requested from Claude. */
public record AiSalaryProposal(
        @JsonPropertyDescription("Recommended salary per month, in the same currency as the statistics")
        double recommendedMonthlySalary,

        @JsonPropertyDescription("Low end of the plausible monthly salary range")
        double rangeLow,

        @JsonPropertyDescription("High end of the plausible monthly salary range")
        double rangeHigh,

        @JsonPropertyDescription("One to three plain-English sentences explaining the recommendation")
        String rationale) {
}
