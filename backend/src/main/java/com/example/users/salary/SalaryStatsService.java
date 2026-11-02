package com.example.users.salary;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Experience is handled in half-year steps (0.5 years = 1 step, so 0 to 120). The band of step s is
 * {@code [s/2, s/2 + 0.5)}; widening adds one neighbouring step on each side until the band is large enough.
 */
@Service
public class SalaryStatsService {

    static final int MAX_STEP = 120;
    /** Smallest band behind a statistical estimate. */
    static final int MIN_BAND_USERS = 5;
    /** Smallest group whose aggregates may be shared with Claude (k-anonymity). */
    static final int MIN_GROUP_USERS = 10;
    private static final int NEIGHBOUR_STEPS = 4;
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final SalaryStatsRepository statsRepository;

    public SalaryStatsService(SalaryStatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    /**
     * Builds the statistics for an experience given in half-year steps.
     *
     * @throws NoSalaryDataException if there are no users at all
     */
    @Transactional(readOnly = true)
    public SalaryStats computeStats(int step) {
        SalaryAggregate all = statsRepository.aggregateAll();
        if (all.getUsers() == 0) {
            throw new NoSalaryDataException();
        }
        Distribution overall = toDistribution(all);
        SalaryBounds bounds = new SalaryBounds(all.getMinSalary(), all.getMaxSalary());
        Map<Integer, Long> usersPerStep = statsRepository.countByExperience().stream()
                .collect(Collectors.toMap(row -> toStep(row.getYears()), ExperienceCount::getUsers, Long::sum));

        Band estimateBand = band(step, MIN_BAND_USERS, usersPerStep, all);
        boolean shareable = overall.users() >= MIN_GROUP_USERS;
        Band promptBand = shareable && estimateBand.salaries().users() < MIN_GROUP_USERS
                ? band(step, MIN_GROUP_USERS, usersPerStep, all) : estimateBand;
        List<ExperienceBucket> neighbours = shareable ? neighbours(step) : List.of();
        return new SalaryStats(years(step), overall, estimateBand, promptBand, neighbours, bounds);
    }

    /** Widens the band step by step until it holds minUsers users; the whole range ends the search. */
    private Band band(int step, int minUsers, Map<Integer, Long> usersPerStep, SalaryAggregate all) {
        int low = step;
        int high = step;
        long users = usersPerStep.getOrDefault(step, 0L);
        while (users < minUsers && (low > 0 || high < MAX_STEP)) {
            low = Math.max(0, low - 1);
            high = Math.min(MAX_STEP, high + 1);
            users = usersInRange(usersPerStep, low, high);
        }
        BigDecimal from = years(low);
        BigDecimal to = years(high + 1);
        SalaryAggregate row = low == 0 && high == MAX_STEP ? all : statsRepository.aggregateByExperience(from, to);
        return new Band(from, to, money(row.getAverage()), toDistribution(row));
    }

    private static long usersInRange(Map<Integer, Long> usersPerStep, int low, int high) {
        return usersPerStep.entrySet().stream()
                .filter(entry -> entry.getKey() >= low && entry.getKey() <= high)
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private List<ExperienceBucket> neighbours(int step) {
        BigDecimal from = years(Math.max(0, step - NEIGHBOUR_STEPS));
        BigDecimal to = years(Math.min(MAX_STEP, step + NEIGHBOUR_STEPS));
        return statsRepository.aggregateByExperienceYear(MIN_GROUP_USERS, from, to).stream()
                .map(row -> new ExperienceBucket(row.getYears().setScale(1, RoundingMode.UNNECESSARY), toDistribution(row)))
                .toList();
    }

    /** Half-year steps to years with one decimal, e.g. 5 to 2.5. */
    static BigDecimal years(int step) {
        return HALF.multiply(BigDecimal.valueOf(step)).setScale(1, RoundingMode.UNNECESSARY);
    }

    static int toStep(BigDecimal years) {
        return years.multiply(BigDecimal.valueOf(2)).intValueExact();
    }

    private static Distribution toDistribution(SalaryAggregate row) {
        return new Distribution(row.getUsers(), money(row.getP10()), money(row.getP25()), money(row.getMedian()),
                money(row.getP75()), money(row.getP90()));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
