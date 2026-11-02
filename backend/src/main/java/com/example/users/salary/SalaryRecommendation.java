package com.example.users.salary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "A monthly salary proposal for a new user")
public record SalaryRecommendation(
        @Schema(description = "Proposed salary per month", example = "82000.00")
        BigDecimal recommendedMonthlySalary,

        @Schema(description = "Low end of the plausible range", example = "74000.00")
        BigDecimal rangeLow,

        @Schema(description = "High end of the plausible range", example = "90000.00")
        BigDecimal rangeHigh,

        @Schema(description = "Short explanation of the proposal",
                example = "Users with 5-9 years earn a median of 80,500/month; 7 years sits mid-band.")
        String rationale,

        @Schema(description = "Who produced the proposal: Claude (AI) or a plain statistical estimate (STATISTICAL)")
        RecommendationSource source,

        @Schema(description = "Number of existing users in the band the proposal is based on", example = "16")
        long basedOnUsers,

        @Schema(description = "Start of the experience band used, in years (inclusive)", example = "2.5")
        BigDecimal bandFromYears,

        @Schema(description = "End of the experience band used, in years (exclusive)", example = "3.0")
        BigDecimal bandToYears,

        @Schema(description = "Claude model used; null when the source is STATISTICAL", example = "claude-opus-5-5",
                nullable = true)
        String model) {

    /** Statistical estimate: band average, p25-p75 range. Used when the AI answer is missing or implausible. */
    public static SalaryRecommendation statistical(SalaryStats stats) {
        Band band = stats.estimateBand();
        String rationale = "Statistical estimate: average monthly salary of %d users with %s to %s years of experience."
                .formatted(band.salaries().users(), plain(band.fromYears()), plain(band.toYears()));
        return new SalaryRecommendation(band.average(), band.salaries().p25(), band.salaries().p75(), rationale,
                RecommendationSource.STATISTICAL, band.salaries().users(), band.fromYears(), band.toYears(), null);
    }

    private static String plain(BigDecimal years) {
        return years.stripTrailingZeros().toPlainString();
    }
}
