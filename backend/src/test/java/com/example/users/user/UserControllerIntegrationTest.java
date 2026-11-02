package com.example.users.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Sql(scripts = "/test-users.sql", executionPhase = BEFORE_TEST_CLASS)
class UserControllerIntegrationTest {

    private static final String USERS_URL = "/api/users";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void firstPageContainsTwentyUsersSortedByUsernameByDefault() throws Exception {
        mockMvc.perform(get(USERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].username").value("user01"))
                .andExpect(jsonPath("$.content[19].username").value("user20"));
    }

    @Test
    void lastPageContainsRemainingUsers() throws Exception {
        mockMvc.perform(get(USERS_URL).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[0].username").value("user21"));
    }

    @Test
    void sortsByUsernameDescending() throws Exception {
        mockMvc.perform(get(USERS_URL).param("sort", "username").param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("user25"));
    }

    @Test
    void sortsByMonthlySalaryDescending() throws Exception {
        mockMvc.perform(get(USERS_URL).param("sort", "monthlySalary").param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("user25"))
                .andExpect(jsonPath("$.content[0].monthlySalary").value(25000.00))
                .andExpect(jsonPath("$.content[1].username").value("user24"));
    }

    @Test
    void sortsByYearsOfExperience() throws Exception {
        mockMvc.perform(get(USERS_URL).param("sort", "yearsOfExperience").param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("user01"))
                .andExpect(jsonPath("$.content[0].yearsOfExperience").value(25.0));

        mockMvc.perform(get(USERS_URL).param("sort", "yearsOfExperience").param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("user25"))
                .andExpect(jsonPath("$.content[0].yearsOfExperience").value(1.0));
    }

    @Test
    void sortsByOrderCountDescendingWithIdTieBreaker() throws Exception {
        mockMvc.perform(get(USERS_URL).param("sort", "orderCount").param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("user04"))
                .andExpect(jsonPath("$.content[0].orderCount").value(4))
                .andExpect(jsonPath("$.content[1].username").value("user09"));
    }

    @Test
    void sortsByOrderCountAscendingIncludingUsersWithoutOrders() throws Exception {
        mockMvc.perform(get(USERS_URL).param("sort", "orderCount").param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("user05"))
                .andExpect(jsonPath("$.content[0].orderCount").value(0));
    }

    @Test
    void rejectsUnknownSortField() throws Exception {
        mockMvc.perform(get(USERS_URL).param("sort", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid query parameter"))
                .andExpect(jsonPath("$.detail").value(containsString("sort")))
                .andExpect(jsonPath("$.detail").value(containsString("monthlySalary, yearsOfExperience")));
    }

    @Test
    void rejectsUnknownDirection() throws Exception {
        mockMvc.perform(get(USERS_URL).param("direction", "sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("direction")));
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get(USERS_URL).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openApiDocumentsUsersEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"/api/users\"")))
                .andExpect(jsonPath("$.info.title").value("Users API"));
    }
}
