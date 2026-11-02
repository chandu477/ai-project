package com.example.users.salary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real advisor with no API key / advisor disabled: no client exists, so the estimate is statistical. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:salary-recommendation-no-ai-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SalaryRecommendationWithoutAiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsStatisticalEstimateWhenAdvisorIsDisabled() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO app_user (username, monthly_salary, years_of_experience)
                SELECT 'user' || CAST(X AS VARCHAR), X * 1000, 5.0 FROM SYSTEM_RANGE(1, 20)""");

        mockMvc.perform(post("/api/salary-recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yearsOfExperience\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("STATISTICAL"))
                .andExpect(jsonPath("$.recommendedMonthlySalary").value(10500.0));
    }
}
