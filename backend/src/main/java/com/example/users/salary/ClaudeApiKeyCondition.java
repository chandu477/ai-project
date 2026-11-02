package com.example.users.salary;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** Matches when the advisor is enabled and an ANTHROPIC_API_KEY is configured. The key is never logged. */
class ClaudeApiKeyCondition implements Condition {

    static final String API_KEY_PROPERTY = "ANTHROPIC_API_KEY";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        boolean enabled = environment.getProperty("salary-advisor.enabled", Boolean.class, true);
        return enabled && StringUtils.hasText(environment.getProperty(API_KEY_PROPERTY));
    }
}
