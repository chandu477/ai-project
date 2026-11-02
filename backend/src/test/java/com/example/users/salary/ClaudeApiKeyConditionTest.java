package com.example.users.salary;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaudeApiKeyConditionTest {

    private final ClaudeApiKeyCondition condition = new ClaudeApiKeyCondition();

    private boolean matches(MockEnvironment environment) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return condition.matches(context, null);
    }

    @Test
    void matchesWhenEnabledByDefaultAndKeyPresent() {
        assertThat(matches(new MockEnvironment().withProperty("ANTHROPIC_API_KEY", "test-key"))).isTrue();
    }

    @Test
    void doesNotMatchWithoutOrWithBlankKey() {
        assertThat(matches(new MockEnvironment())).isFalse();
        assertThat(matches(new MockEnvironment().withProperty("ANTHROPIC_API_KEY", "  "))).isFalse();
    }

    @Test
    void doesNotMatchWhenDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("ANTHROPIC_API_KEY", "test-key")
                .withProperty("salary-advisor.enabled", "false");

        assertThat(matches(environment)).isFalse();
    }
}
