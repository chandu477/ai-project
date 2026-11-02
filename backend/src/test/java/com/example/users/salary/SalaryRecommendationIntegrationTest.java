package com.example.users.salary;

import com.example.users.user.UserCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:salary-recommendation-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SalaryRecommendationIntegrationTest {

    private static final String URL = "/api/salary-recommendations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockitoBean
    private ClaudeSalaryAdvisor advisor;

    /** 20 users with 5 years of experience and salaries 1000..20000 (median 10500, p25 5750, p75 15250). */
    @BeforeEach
    void seedUsers() {
        reset(advisor);
        cacheManager.getCache(SalaryRecommendationService.CACHE_NAME).clear();
        jdbcTemplate.update("DELETE FROM customer_order");
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update("""
                INSERT INTO app_user (username, monthly_salary, years_of_experience)
                SELECT 'user' || CAST(X AS VARCHAR), X * 1000, 5.0 FROM SYSTEM_RANGE(1, 20)""");
    }

    private ResultActions recommend(String body) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions recommend(int years) throws Exception {
        return recommend("{\"yearsOfExperience\": %d}".formatted(years));
    }

    private static Optional<AiAdvice> advice(double recommended, double low, double high) {
        return advice(recommended, low, high, "Based on peers.");
    }

    private static Optional<AiAdvice> advice(double recommended, double low, double high, String rationale) {
        return Optional.of(new AiAdvice(new AiSalaryProposal(recommended, low, high, rationale), "claude-opus-5-5-20261101"));
    }

    @Test
    void validAiAnswerIsReturnedWithSourceAi() throws Exception {
        when(advisor.propose(any())).thenReturn(advice(11000, 9000, 13000));

        recommend(5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.recommendedMonthlySalary").value(11000.0))
                .andExpect(jsonPath("$.rangeLow").value(9000.0))
                .andExpect(jsonPath("$.rangeHigh").value(13000.0))
                .andExpect(jsonPath("$.rationale").value("Based on peers."))
                .andExpect(jsonPath("$.basedOnUsers").value(20))
                .andExpect(jsonPath("$.bandFromYears").value(5.0))
                .andExpect(jsonPath("$.bandToYears").value(5.5))
                .andExpect(jsonPath("$.model").value("claude-opus-5-5-20261101"));
    }

    @Test
    void lowAboveHighFallsBackToStatistical() throws Exception {
        when(advisor.propose(any())).thenReturn(advice(11000, 13000, 9000));

        recommend(5).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("STATISTICAL"));
    }

    @Test
    void recommendationFarAboveObservedMaxFallsBackToStatistical() throws Exception {
        when(advisor.propose(any())).thenReturn(advice(200000, 190000, 210000));

        recommend(5).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("STATISTICAL"));
    }

    @Test
    void emptyAdvisorResultReturnsStatisticalEstimate() throws Exception {
        when(advisor.propose(any())).thenReturn(Optional.empty());

        recommend(5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("STATISTICAL"))
                .andExpect(jsonPath("$.recommendedMonthlySalary").value(10500.0))
                .andExpect(jsonPath("$.rationale").value(
                        "Statistical estimate: average monthly salary of 20 users with 5 to 5.5 years of experience."))
                .andExpect(jsonPath("$.bandFromYears").value(5.0))
                .andExpect(jsonPath("$.bandToYears").value(5.5))
                .andExpect(jsonPath("$.rangeLow").value(5750.0))
                .andExpect(jsonPath("$.rangeHigh").value(15250.0))
                .andExpect(jsonPath("$.basedOnUsers").value(20))
                .andExpect(jsonPath("$.model").doesNotExist());
    }

    @Test
    void invalidExperienceReturns400() throws Exception {
        recommend(-1).andExpect(status().isBadRequest());
        recommend(61).andExpect(status().isBadRequest());
        recommend("{}").andExpect(status().isBadRequest());
        verifyNoInteractions(advisor);
    }

    @Test
    void noUsersReturns422() throws Exception {
        jdbcTemplate.update("DELETE FROM app_user");

        recommend(5).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(advisor);
    }

    @Test
    void secondIdenticalCallIsServedFromCache() throws Exception {
        when(advisor.propose(any())).thenReturn(advice(11000, 9000, 13000));

        recommend(5).andExpect(status().isOk());
        recommend(5).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("AI"));

        verify(advisor, times(1)).propose(any());
    }

    @Test
    void statisticalFallbackIsNotCached() throws Exception {
        when(advisor.propose(any())).thenReturn(Optional.empty());
        recommend(5).andExpect(jsonPath("$.source").value("STATISTICAL"));

        when(advisor.propose(any())).thenReturn(advice(11000, 9000, 13000));
        recommend(5).andExpect(jsonPath("$.source").value("AI"));

        verify(advisor, times(2)).propose(any());
    }

    @Test
    void rangeOutsideObservedWindowFallsBackToStatistical() throws Exception {
        // Observed salaries are 1000..20000, so the allowed window is 500..30000.
        when(advisor.propose(any())).thenReturn(advice(11000, 100, 13000));
        recommend(5).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("STATISTICAL"));

        cacheManager.getCache(SalaryRecommendationService.CACHE_NAME).clear();
        when(advisor.propose(any())).thenReturn(advice(11000, 9000, 31000));
        recommend(5).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("STATISTICAL"));
    }

    @Test
    void overlongOrBlankRationaleFallsBackToStatistical() throws Exception {
        when(advisor.propose(any())).thenReturn(advice(11000, 9000, 13000, "x".repeat(601)));
        recommend(5).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("STATISTICAL"));

        cacheManager.getCache(SalaryRecommendationService.CACHE_NAME).clear();
        when(advisor.propose(any())).thenReturn(advice(11000, 9000, 13000, "  "));
        recommend(5).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("STATISTICAL"));
    }

    @Test
    void invalidExperienceBodyHasFieldErrors() throws Exception {
        recommend(61)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.yearsOfExperience").value("Years of experience must be between 0 and 60"));
    }

    @Test
    void creatingAUserEvictsTheCache() throws Exception {
        when(advisor.propose(any())).thenReturn(advice(11000, 9000, 13000));

        recommend(5).andExpect(status().isOk());
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new.user\",\"yearsOfExperience\":5,\"monthlySalary\":12000}"))
                .andExpect(status().isCreated());
        recommend(5).andExpect(status().isOk());

        verify(advisor, times(2)).propose(any());
    }

    @Test
    void userCreatedEventEvictsTheCache() throws Exception {
        when(advisor.propose(any())).thenReturn(advice(11000, 9000, 13000));

        recommend(5).andExpect(status().isOk());
        eventPublisher.publishEvent(new UserCreatedEvent(1L));
        recommend(5).andExpect(status().isOk());

        verify(advisor, times(2)).propose(any());
    }

    @Test
    void concurrentIdenticalRequestsShareOneAdvisorCall() throws Exception {
        CountDownLatch inAdvisor = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(advisor.propose(any())).thenAnswer(invocation -> {
            inAdvisor.countDown();
            release.await(5, TimeUnit.SECONDS);
            return advice(11000, 9000, 13000);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> recommend(5));
            assertThat(inAdvisor.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> recommend(5));
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        verify(advisor, times(1)).propose(any());
    }

    @Test
    void differentExperiencesAreNotCoalesced() throws Exception {
        when(advisor.propose(any())).thenReturn(advice(11000, 9000, 13000));

        recommend(5).andExpect(status().isOk());
        recommend(6).andExpect(status().isOk());

        verify(advisor, times(2)).propose(any());
    }

    @Test
    void experienceMustBeInHalfYearSteps() throws Exception {
        recommend("{\"yearsOfExperience\": 2.3}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.yearsOfExperience").value("Use steps of 0.5 years"));
        verifyNoInteractions(advisor);
    }

    @Test
    void halfYearExperienceIsAccepted() throws Exception {
        when(advisor.propose(any())).thenReturn(Optional.empty());

        recommend("{\"yearsOfExperience\": 2.5}").andExpect(status().isOk());
        recommend("{\"yearsOfExperience\": 0}").andExpect(status().isOk());
        recommend("{\"yearsOfExperience\": 60.0}").andExpect(status().isOk());
    }

    @Test
    void equalExperienceWrittenDifferentlySharesOneCacheEntry() throws Exception {
        when(advisor.propose(any())).thenReturn(advice(11000, 9000, 13000));

        recommend("{\"yearsOfExperience\": 5}").andExpect(status().isOk());
        recommend("{\"yearsOfExperience\": 5.0}").andExpect(status().isOk());
        recommend("{\"yearsOfExperience\": 5.00}").andExpect(status().isOk());

        verify(advisor, times(1)).propose(any());
    }

    @Test
    void sparseBandIsWidenedToNeighbours() throws Exception {
        when(advisor.propose(any())).thenReturn(Optional.empty());
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update("""
                INSERT INTO app_user (username, monthly_salary, years_of_experience)
                SELECT 'near' || CAST(X AS VARCHAR), 1000 * X, 8.0 + MOD(X, 3) * 0.5 FROM SYSTEM_RANGE(1, 6)""");

        // 8.0, 8.5 and 9.0 hold two users each; asking for 8.5 widens [8.5, 9.0) to [8.0, 9.5).
        recommend("{\"yearsOfExperience\": 8.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basedOnUsers").value(6))
                .andExpect(jsonPath("$.bandFromYears").value(8.0))
                .andExpect(jsonPath("$.bandToYears").value(9.5))
                .andExpect(jsonPath("$.recommendedMonthlySalary").value(3500.0));
    }

    @Test
    void followerGetsStatisticalEstimateWhenLeaderDiesWithAnError() throws Exception {
        CountDownLatch inAdvisor = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(advisor.propose(any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() > 1) {
                return Optional.empty();
            }
            inAdvisor.countDown();
            release.await(5, TimeUnit.SECONDS);
            throw new StackOverflowError("simulated");
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> recommend(5));
            assertThat(inAdvisor.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> follower = executor.submit(() ->
                    recommend(5).andReturn().getResponse().getContentAsString());
            release.countDown();

            assertThat(follower.get(5, TimeUnit.SECONDS)).contains("\"source\":\"STATISTICAL\"");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
