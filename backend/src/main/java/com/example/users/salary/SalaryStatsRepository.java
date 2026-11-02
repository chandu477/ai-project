package com.example.users.salary;

import com.example.users.user.AppUser;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only salary aggregates over users, computed in SQL (PERCENTILE_CONT works on H2 2.x and
 * PostgreSQL), so no individual salaries are loaded into memory. Aliases are quoted to keep their case.
 */
public interface SalaryStatsRepository extends Repository<AppUser, Long> {

    String AGGREGATES = """
            COUNT(*) AS "users",
            MIN(monthly_salary) AS "minSalary",
            MAX(monthly_salary) AS "maxSalary",
            AVG(monthly_salary) AS "average",
            PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY monthly_salary) AS "p10",
            PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY monthly_salary) AS "p25",
            PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY monthly_salary) AS "median",
            PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY monthly_salary) AS "p75",
            PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY monthly_salary) AS "p90"
            """;

    @Query(value = "SELECT " + AGGREGATES + "FROM app_user", nativeQuery = true)
    SalaryAggregate aggregateAll();

    /** Users per distinct experience value; at most 121 rows. Used to pick the band, never sent anywhere. */
    @Query(value = """
            SELECT years_of_experience AS "years", COUNT(*) AS "users"
            FROM app_user
            GROUP BY years_of_experience
            """, nativeQuery = true)
    List<ExperienceCount> countByExperience();

    /** Aggregates of the band {@code [fromYears, toYears)}. */
    @Query(value = "SELECT " + AGGREGATES + """
            FROM app_user
            WHERE years_of_experience >= :fromYears AND years_of_experience < :toYears
            """, nativeQuery = true)
    SalaryAggregate aggregateByExperience(@Param("fromYears") BigDecimal fromYears,
                                          @Param("toYears") BigDecimal toYears);

    /** One row per experience value in {@code [fromYears, toYears]} that has at least minUsers users. */
    @Query(value = "SELECT years_of_experience AS \"years\", " + AGGREGATES + """
            FROM app_user
            WHERE years_of_experience BETWEEN :fromYears AND :toYears
            GROUP BY years_of_experience
            HAVING COUNT(*) >= :minUsers
            ORDER BY years_of_experience
            """, nativeQuery = true)
    List<ExperienceAggregate> aggregateByExperienceYear(@Param("minUsers") int minUsers,
                                                        @Param("fromYears") BigDecimal fromYears,
                                                        @Param("toYears") BigDecimal toYears);
}
