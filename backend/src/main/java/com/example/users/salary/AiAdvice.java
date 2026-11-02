package com.example.users.salary;

/** Claude's proposal together with the model that actually answered. */
public record AiAdvice(AiSalaryProposal proposal, String model) {
}
