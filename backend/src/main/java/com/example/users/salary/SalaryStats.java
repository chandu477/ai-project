package com.example.users.salary;

import java.math.BigDecimal;
import java.util.List;

/**
 * Exact salary statistics for one requested experience. Internal: the statistical estimate shown to the
 * user uses {@code estimateBand} (at least 5 users); {@code bounds} validate AI answers. Claude only gets
 * the rounded, privacy-reduced {@link PromptStats} copy.
 *
 * @param estimateBand the half-year band, widened to at least 5 users
 * @param promptBand   the same band widened independently to at least 10 users (shared with Claude)
 * @param neighbours   half-year bands near the target with at least 10 users each; empty if not shareable
 */
public record SalaryStats(BigDecimal yearsOfExperience, Distribution overall, Band estimateBand, Band promptBand,
                          List<ExperienceBucket> neighbours, SalaryBounds bounds) {

    static final int PROMPT_ROUNDING = 1000;

    /** True if there are enough users in total to share anything with Claude. */
    public boolean isShareable() {
        return overall.users() >= SalaryStatsService.MIN_GROUP_USERS;
    }

    PromptStats forPrompt() {
        List<ExperienceBucket> shareable = neighbours.stream()
                .filter(bucket -> bucket.salaries().users() >= SalaryStatsService.MIN_GROUP_USERS)
                .map(bucket -> new ExperienceBucket(bucket.fromYears(), bucket.salaries().roundedTo(PROMPT_ROUNDING)))
                .toList();
        return new PromptStats(yearsOfExperience, promptBand.roundedTo(PROMPT_ROUNDING),
                overall.roundedTo(PROMPT_ROUNDING), shareable);
    }
}
