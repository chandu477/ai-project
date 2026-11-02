package com.example.users.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:user-creation-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class UserCreationIntegrationTest {

    private static final String USERS_URL = "/api/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void emptyDatabase() {
        jdbcTemplate.update("DELETE FROM customer_order");
        jdbcTemplate.update("DELETE FROM app_user");
    }

    private ResultActions postUser(String username, Object years, Object salary) throws Exception {
        String body = """
                {"username": %s, "yearsOfExperience": %s, "monthlySalary": %s}""".formatted(username, years, salary);
        return mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void createsUserAndReturnsLocationAndBody() throws Exception {
        postUser("\"neha.k\"", 7, "82000.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/api/users/\\d+")))
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.username").value("neha.k"))
                .andExpect(jsonPath("$.orderCount").value(0))
                .andExpect(jsonPath("$.monthlySalary").value(82000.00))
                .andExpect(jsonPath("$.yearsOfExperience").value(7.0));
    }

    @Test
    void createdUserAppearsInListingAndCanBeFetchedByLocation() throws Exception {
        String location = postUser("\"neha.k\"", 7, "82000.00")
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(USERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("neha.k"))
                .andExpect(jsonPath("$.content[0].orderCount").value(0));

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("neha.k"))
                .andExpect(jsonPath("$.yearsOfExperience").value(7.0));
    }

    @Test
    void getUserReturnsOrderCount() throws Exception {
        jdbcTemplate.update("INSERT INTO app_user (username, monthly_salary, years_of_experience) VALUES ('with.orders', 100, 1)");
        Long id = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE username = 'with.orders'", Long.class);
        jdbcTemplate.update("INSERT INTO customer_order (user_id, amount) VALUES (?, 5), (?, 6)", id, id);

        mockMvc.perform(get(USERS_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCount").value(2));
    }

    @Test
    void getUnknownUserReturns404ProblemDetail() throws Exception {
        mockMvc.perform(get(USERS_URL + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        postUser("\"neha.k\"", 7, "82000.00").andExpect(status().isCreated());

        postUser("\"neha.k\"", 3, "50000.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(endsWith("is already taken")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"ab\"", "\"Neha\"", "\"has space\"", "null"})
    void invalidUsernameReturns400WithFieldError(String username) throws Exception {
        postUser(username, 7, "82000.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").isNotEmpty())
                .andExpect(jsonPath("$.errors.length()").value(1));
    }

    @Test
    void tooLongUsernameReturns400() throws Exception {
        postUser("\"" + "a".repeat(51) + "\"", 7, "82000.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").value("Username must be 3 to 50 characters"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "61", "null"})
    void invalidExperienceReturns400WithFieldError(String years) throws Exception {
        postUser("\"neha.k\"", years, "82000.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.yearsOfExperience").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "null", "12345678901.00", "100.123"})
    void invalidSalaryReturns400WithFieldError(String salary) throws Exception {
        postUser("\"neha.k\"", 7, salary)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.monthlySalary").isNotEmpty());
    }

    @Test
    void tooManyDigitsGivesFriendlyMessageAndProblemShape() throws Exception {
        postUser("\"neha.k\"", 7, "100.123")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.monthlySalary").value("At most 10 digits and 2 decimals"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.3", "7.9", "0.25", "10.75"})
    void experienceNotInHalfYearStepsReturns400(String years) throws Exception {
        postUser("\"neha.k\"", years, "82000.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.yearsOfExperience").value("Use steps of 0.5 years"));

        mockMvc.perform(get(USERS_URL)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.5", "60.5", "61"})
    void experienceOutOfRangeReturns400(String years) throws Exception {
        postUser("\"neha.k\"", years, "82000.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.yearsOfExperience").value("Years of experience must be between 0 and 60"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.5", "2.5", "7", "7.0", "7.50", "60", "60.0"})
    void halfYearExperienceIsAcceptedAndStoredWithOneDecimal(String years) throws Exception {
        postUser("\"neha.k\"", years, "82000.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yearsOfExperience").value(Double.parseDouble(years)));
    }

    @Test
    void nonNumericExperienceReturnsPlain400() throws Exception {
        postUser("\"neha.k\"", "\"seven\"", "82000.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void multipleInvalidFieldsAreAllReported() throws Exception {
        postUser("\"X\"", 99, "-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").isNotEmpty())
                .andExpect(jsonPath("$.errors.yearsOfExperience").isNotEmpty())
                .andExpect(jsonPath("$.errors.monthlySalary").isNotEmpty());
    }

    @Test
    void nonNumericSalaryReturnsPlain400() throws Exception {
        postUser("\"neha.k\"", 7, "\"abc\"")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void boundaryValuesAreAccepted() throws Exception {
        postUser("\"abc\"", 0, "0.01").andExpect(status().isCreated());
        postUser("\"" + "a".repeat(50) + "\"", 60, "9999999999.99").andExpect(status().isCreated());
    }
}
