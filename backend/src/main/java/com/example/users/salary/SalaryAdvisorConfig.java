package com.example.users.salary;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableCaching
@EnableConfigurationProperties(SalaryAdvisorProperties.class)
public class SalaryAdvisorConfig {

    /** Only created when the advisor is enabled and an API key is present. */
    @Bean
    @Conditional(ClaudeApiKeyCondition.class)
    public AnthropicClient anthropicClient(SalaryAdvisorProperties properties, Environment environment) {
        return AnthropicOkHttpClient.builder()
                .apiKey(environment.getRequiredProperty(ClaudeApiKeyCondition.API_KEY_PROPERTY))
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries())
                .build();
    }
}
