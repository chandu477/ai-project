package com.example.users.salary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryStatsServiceTest {

    @Mock
    private SalaryStatsRepository repository;

    @InjectMocks
    private SalaryStatsService service;

    /** A stub aggregate row: percentiles are derived from the average to keep the tests short. */
    private record Row(BigDecimal years, Long users, int average) implements ExperienceAggregate {
        @Override public Long getUsers() { return users; }
        @Override public BigDecimal getMinSalary() { return value(average - 1000); }
        @Override public BigDecimal getMaxSalary() { return value(average + 1000); }
        @Override public BigDecimal getAverage() { return value(average); }
        @Override public BigDecimal getP10() { return value(average - 400); }
        @Override public BigDecimal getP25() { return value(average - 200); }
        @Override public BigDecimal getMedian() { return value(average); }
        @Override public BigDecimal getP75() { return value(average + 200); }
        @Override public BigDecimal getP90() { return value(average + 400); }
        @Override public BigDecimal getYears() { return years; }
    }

    private record Count(BigDecimal years, Long users) implements ExperienceCount {
        @Override public BigDecimal getYears() { return years; }
        @Override public Long getUsers() { return users; }
    }

    private static BigDecimal value(int amount) {
        return BigDecimal.valueOf(amount);
    }

    private static Row row(long users, int average) {
        return new Row(null, users, average);
    }

    private static Count count(String years, long users) {
        return new Count(new BigDecimal(years), users);
    }

    private static BigDecimal years(String years) {
        return new BigDecimal(years);
    }

    @Test
    void exactBandIsUsedWhenItHasEnoughUsers() {
        when(repository.aggregateAll()).thenReturn(row(100, 5000));
        when(repository.countByExperience()).thenReturn(List.of(count("2.5", 16), count("3.0", 40)));
        when(repository.aggregateByExperience(years("2.5"), years("3.0"))).thenReturn(row(16, 4000));
        when(repository.aggregateByExperienceYear(10, years("0.5"), years("4.5"))).thenReturn(List.of());

        SalaryStats stats = service.computeStats(5);

        assertThat(stats.yearsOfExperience()).isEqualByComparingTo("2.5");
        assertThat(stats.estimateBand().fromYears()).isEqualByComparingTo("2.5");
        assertThat(stats.estimateBand().toYears()).isEqualByComparingTo("3.0");
        assertThat(stats.estimateBand().salaries().users()).isEqualTo(16);
        assertThat(stats.estimateBand().average()).isEqualByComparingTo("4000");
        assertThat(stats.estimateBand().salaries().p25()).isEqualByComparingTo("3800");
        assertThat(stats.promptBand()).isEqualTo(stats.estimateBand());
        assertThat(stats.bounds().min()).isEqualByComparingTo("4000");
        assertThat(stats.bounds().max()).isEqualByComparingTo("6000");
        assertThat(stats.isShareable()).isTrue();
    }

    @Test
    void sparseBandWidensByOneNeighbourOnEachSideUntilFiveUsers() {
        when(repository.aggregateAll()).thenReturn(row(100, 5000));
        when(repository.countByExperience())
                .thenReturn(List.of(count("2.0", 2), count("2.5", 1), count("3.0", 3), count("3.5", 4), count("9", 50)));
        // 2.5 alone: 1 user. One step each side, [2.0, 3.5): 6 users, enough for the estimate (5).
        // Two steps each side, [1.5, 4.0): 10 users, enough for the prompt (10).
        when(repository.aggregateByExperience(years("2.0"), years("3.5"))).thenReturn(row(6, 4000));
        when(repository.aggregateByExperience(years("1.5"), years("4.0"))).thenReturn(row(10, 4100));
        when(repository.aggregateByExperienceYear(10, years("0.5"), years("4.5"))).thenReturn(List.of());

        SalaryStats stats = service.computeStats(5);

        assertThat(stats.estimateBand().fromYears()).isEqualByComparingTo("2.0");
        assertThat(stats.estimateBand().toYears()).isEqualByComparingTo("3.5");
        assertThat(stats.estimateBand().salaries().users()).isEqualTo(6);
        assertThat(stats.promptBand().fromYears()).isEqualByComparingTo("1.5");
        assertThat(stats.promptBand().toYears()).isEqualByComparingTo("4.0");
        assertThat(stats.promptBand().salaries().users()).isEqualTo(10);
    }

    @Test
    void lowerEndIsClampedAtZero() {
        when(repository.aggregateAll()).thenReturn(row(100, 5000));
        when(repository.countByExperience()).thenReturn(List.of(count("0.0", 1), count("0.5", 2), count("1.0", 3), count("2.0", 10)));
        when(repository.aggregateByExperience(years("0.0"), years("1.5"))).thenReturn(row(6, 3000));
        when(repository.aggregateByExperience(years("0.0"), years("2.5"))).thenReturn(row(16, 3100));
        when(repository.aggregateByExperienceYear(10, years("0.0"), years("2.0"))).thenReturn(List.of());

        SalaryStats stats = service.computeStats(0);

        assertThat(stats.estimateBand().fromYears()).isEqualByComparingTo("0");
        assertThat(stats.estimateBand().toYears()).isEqualByComparingTo("1.5");
        assertThat(stats.estimateBand().salaries().users()).isEqualTo(6);
    }

    @Test
    void usesAllUsersWhenEvenTheWholeRangeIsTooSmall() {
        when(repository.aggregateAll()).thenReturn(row(4, 5000));
        when(repository.countByExperience()).thenReturn(List.of(count("2.5", 1), count("40", 3)));

        SalaryStats stats = service.computeStats(5);

        assertThat(stats.estimateBand().fromYears()).isEqualByComparingTo("0");
        assertThat(stats.estimateBand().toYears()).isEqualByComparingTo("60.5");
        assertThat(stats.estimateBand().salaries().users()).isEqualTo(4);
        assertThat(stats.isShareable()).isFalse();
        assertThat(stats.neighbours()).isEmpty();
        verify(repository, never()).aggregateByExperience(any(), any());
        verify(repository, never()).aggregateByExperienceYear(org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void fewerThanTenUsersAreNotShareableButStillGetAnEstimate() {
        when(repository.aggregateAll()).thenReturn(row(9, 5000));
        when(repository.countByExperience()).thenReturn(List.of(count("2.5", 6), count("40", 3)));
        when(repository.aggregateByExperience(years("2.5"), years("3.0"))).thenReturn(row(6, 4000));

        SalaryStats stats = service.computeStats(5);

        assertThat(stats.isShareable()).isFalse();
        assertThat(stats.estimateBand().salaries().users()).isEqualTo(6);
        assertThat(stats.neighbours()).isEmpty();
    }

    @Test
    void emptyDatabaseThrows() {
        when(repository.aggregateAll()).thenReturn(row(0, 0));

        assertThatThrownBy(() -> service.computeStats(5)).isInstanceOf(NoSalaryDataException.class);
    }

    @Test
    void statisticalEstimateIsTheBandAverageWithQuartileRange() {
        Distribution salaries = new Distribution(16, value(3000), value(4000), value(5000), value(6000), value(7000));
        Band band = new Band(years("2.5"), years("3.0"), value(5300), salaries);
        SalaryStats stats = new SalaryStats(years("2.5"), salaries, band, band, List.of(),
                new SalaryBounds(value(1000), value(9000)));

        SalaryRecommendation estimate = SalaryRecommendation.statistical(stats);

        assertThat(estimate.source()).isEqualTo(RecommendationSource.STATISTICAL);
        assertThat(estimate.recommendedMonthlySalary()).isEqualByComparingTo("5300");
        assertThat(estimate.rangeLow()).isEqualByComparingTo("4000");
        assertThat(estimate.rangeHigh()).isEqualByComparingTo("6000");
        assertThat(estimate.basedOnUsers()).isEqualTo(16);
        assertThat(estimate.bandFromYears()).isEqualByComparingTo("2.5");
        assertThat(estimate.bandToYears()).isEqualByComparingTo("3.0");
        assertThat(estimate.model()).isNull();
        assertThat(estimate.rationale())
                .isEqualTo("Statistical estimate: average monthly salary of 16 users with 2.5 to 3 years of experience.");
    }
}
