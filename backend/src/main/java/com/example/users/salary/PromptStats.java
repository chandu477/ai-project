package com.example.users.salary;

import java.math.BigDecimal;
import java.util.List;

/**
 * What is serialized into the Claude prompt. Guarantees:
 * <ul>
 *   <li>exactly one average: the target band's (a group of at least
 *       {@value SalaryStatsService#MIN_GROUP_USERS} users); every other group carries percentiles only,
 *       so no average can be subtracted from another to reveal a smaller group;</li>
 *   <li>only counts, that average and the percentiles p10, p25, median, p75 and p90: no min or max,
 *       no usernames, no individual records;</li>
 *   <li>every salary rounded to the nearest 1,000;</li>
 *   <li>only groups with at least {@value SalaryStatsService#MIN_GROUP_USERS} users.</li>
 * </ul>
 */
public record PromptStats(BigDecimal yearsOfExperience, Band targetBand, Distribution overall,
                          List<ExperienceBucket> neighbouringBands) {
}
