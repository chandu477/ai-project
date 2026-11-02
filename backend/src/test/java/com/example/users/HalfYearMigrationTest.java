package com.example.users;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Applies V1 first, adds whole-year users, then V2, to prove existing values are preserved. */
class HalfYearMigrationTest {

    @Test
    void v2KeepsWholeYearValuesAndAllowsHalfYears() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:migration-v2-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("1").load().migrate();
        jdbc.update("INSERT INTO app_user (username, monthly_salary, years_of_experience) VALUES ('old.user', 5000, 12)");
        jdbc.update("INSERT INTO app_user (username, monthly_salary, years_of_experience) VALUES ('older.user', 6000, 30)");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("SELECT years_of_experience FROM app_user WHERE username = 'old.user'",
                BigDecimal.class)).isEqualByComparingTo("12");
        assertThat(jdbc.queryForObject("SELECT years_of_experience FROM app_user WHERE username = 'older.user'",
                BigDecimal.class)).isEqualByComparingTo("30");
        jdbc.update("INSERT INTO app_user (username, monthly_salary, years_of_experience) VALUES ('half.user', 5000, 2.5)");
    }
}
