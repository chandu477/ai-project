package com.example.users.salary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the native aggregate SQL (PERCENTILE_CONT ... WITHIN GROUP) against H2. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:salary-stats-repository-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class SalaryStatsRepositoryIntegrationTest {

    @Autowired
    private SalaryStatsRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void emptyDatabase() {
        jdbcTemplate.update("DELETE FROM app_user");
    }

    private void insertUsers(double years, int... salaries) {
        for (int salary : salaries) {
            jdbcTemplate.update("INSERT INTO app_user (username, monthly_salary, years_of_experience) VALUES (?, ?, ?)",
                    "u" + years + "." + salary, salary, years);
        }
    }

    @Test
    void aggregatesOfOddCountUseTheMiddleValues() {
        insertUsers(1, 10, 20, 30, 40, 50);

        SalaryAggregate all = repository.aggregateAll();

        assertThat(all.getUsers()).isEqualTo(5);
        assertThat(all.getMinSalary()).isEqualByComparingTo("10");
        assertThat(all.getMaxSalary()).isEqualByComparingTo("50");
        assertThat(all.getP10()).isEqualByComparingTo("14");
        assertThat(all.getP25()).isEqualByComparingTo("20");
        assertThat(all.getAverage()).isEqualByComparingTo("30");
        assertThat(all.getMedian()).isEqualByComparingTo("30");
        assertThat(all.getP75()).isEqualByComparingTo("40");
        assertThat(all.getP90()).isEqualByComparingTo("46");
    }

    @Test
    void percentilesOfEvenCountInterpolateLinearly() {
        insertUsers(1, 10, 20, 30, 40);

        SalaryAggregate all = repository.aggregateAll();

        assertThat(all.getP25()).isEqualByComparingTo("17.5");
        assertThat(all.getMedian()).isEqualByComparingTo("25");
        assertThat(all.getP75()).isEqualByComparingTo("32.5");
    }

    @Test
    void singleUserHasThatSalaryEverywhere() {
        insertUsers(3, 5000);

        SalaryAggregate all = repository.aggregateAll();

        assertThat(all.getUsers()).isEqualTo(1);
        assertThat(all.getP25()).isEqualByComparingTo("5000");
        assertThat(all.getP90()).isEqualByComparingTo("5000");
    }

    @Test
    void emptyTableCountsZero() {
        assertThat(repository.aggregateAll().getUsers()).isZero();
    }

    @Test
    void bandIsHalfOpenAndAveragesItsUsers() {
        insertUsers(2, 100, 200);
        insertUsers(2.5, 1000, 3000);
        insertUsers(3, 9000);

        SalaryAggregate band = repository.aggregateByExperience(new BigDecimal("2.5"), new BigDecimal("3.0"));

        assertThat(band.getUsers()).isEqualTo(2);
        assertThat(band.getAverage()).isEqualByComparingTo("2000");
        assertThat(band.getMedian()).isEqualByComparingTo("2000");
        assertThat(band.getP25()).isEqualByComparingTo("1500");
        assertThat(band.getP75()).isEqualByComparingTo("2500");
        assertThat(repository.aggregateByExperience(new BigDecimal("20"), new BigDecimal("30")).getUsers()).isZero();
    }

    @Test
    void countsUsersPerExperienceValue() {
        insertUsers(2, 100, 200);
        insertUsers(2.5, 1000);

        List<ExperienceCount> counts = repository.countByExperience();

        assertThat(counts).extracting(c -> c.getYears().doubleValue(), ExperienceCount::getUsers)
                .containsExactlyInAnyOrder(org.assertj.core.api.Assertions.tuple(2.0, 2L),
                        org.assertj.core.api.Assertions.tuple(2.5, 1L));
    }

    @Test
    void experienceBucketsExcludeGroupsBelowTheMinimumAndOutsideTheRange() {
        insertUsers(2, 100, 200, 300, 400);
        insertUsers(5.5, 1000, 2000, 3000, 4000, 5000);
        insertUsers(9, 9000);

        List<ExperienceAggregate> buckets = repository.aggregateByExperienceYear(5, new BigDecimal("4"), new BigDecimal("10"));

        assertThat(buckets).singleElement().satisfies(bucket -> {
            assertThat(bucket.getYears()).isEqualByComparingTo("5.5");
            assertThat(bucket.getUsers()).isEqualTo(5);
            assertThat(bucket.getMedian()).isEqualByComparingTo("3000");
        });
    }

    @Test
    void databaseRejectsNonHalfYearValues() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertUsers(2.3, 100))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertUsers(60.5, 100))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
