package com.example.users.salary;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaUsage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import com.anthropic.models.beta.messages.StructuredTextBlock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * Asks Claude for a salary proposal grounded in aggregated statistics. Every failure (no client,
 * refusal, truncation, API or network error) yields an empty result so the caller can fall back.
 */
@Component
public class ClaudeSalaryAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ClaudeSalaryAdvisor.class);
    private static final long MAX_TOKENS = 16000;

    private static final String SYSTEM_PROMPT = """
            You are a compensation analyst. Propose a monthly salary for a new employee with the given \
            years of experience.
            The user message holds statistics rounded to the nearest 1,000: the targetBand (the half-year \
            experience band of the new employee, widened to neighbouring bands if it had few users) with its \
            average and percentiles p10 to p90, the neighbouringBands with percentiles only, and the overall \
            percentiles. Anchor your proposal on the targetBand average, adjust only with a clear reason from the \
            neighbouring bands or overall figures, and explain any deviation. \
            Keep rangeLow <= recommendedMonthlySalary <= rangeHigh.
            Write the rationale in one to three short, plain-English sentences that cite the figures you used.""";

    private final SalaryAdvisorProperties properties;
    private final ObjectMapper objectMapper;
    private final AnthropicClient client;
    private final Semaphore callPermits;

    public ClaudeSalaryAdvisor(SalaryAdvisorProperties properties, ObjectMapper objectMapper,
                               ObjectProvider<AnthropicClient> clientProvider) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = clientProvider.getIfAvailable();
        this.callPermits = new Semaphore(properties.maxConcurrentCalls());
    }

    public Optional<AiAdvice> propose(SalaryStats stats) {
        if (client == null || !stats.isShareable()) {
            return Optional.empty();
        }
        if (!callPermits.tryAcquire()) {
            log.warn("Salary advisor is at its concurrency limit, using the statistical estimate");
            return Optional.empty();
        }
        try {
            return callClaude(stats);
        } finally {
            callPermits.release();
        }
    }

    private Optional<AiAdvice> callClaude(SalaryStats stats) {
        try {
            StructuredMessage<AiSalaryProposal> response = client.beta().messages()
                    .create(buildParams(stats));
            return extractProposal(response);
        } catch (RateLimitException ex) {
            log.warn("Salary advisor rate limited by the Claude API");
        } catch (AnthropicServiceException ex) {
            log.warn("Salary advisor call failed with HTTP status {}", ex.statusCode());
        } catch (AnthropicException ex) {
            log.warn("Salary advisor call failed: {}", ex.getClass().getSimpleName());
        } catch (JsonProcessingException ex) {
            log.warn("Salary statistics could not be serialized for the prompt");
        } catch (RuntimeException ex) { // NOSONAR java:S2221 - any unexpected SDK/parsing failure must fall back, not 500
            log.warn("Salary advisor failed unexpectedly: {}", ex.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    private StructuredMessageCreateParams<AiSalaryProposal> buildParams(SalaryStats stats)
            throws JsonProcessingException {
        BetaOutputConfig.Effort effort = BetaOutputConfig.Effort.of(properties.effort());
        // Thinking is always on for this model; depth is controlled with effort only.
        // The beta endpoint is used for the server-side refusal fallback (fallbacks "default").
        return MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(MAX_TOKENS)
                .outputConfig(AiSalaryProposal.class, effort, JsonSchemaLocalValidation.YES)
                .betas(List.of(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01))
                .fallbacksDefault()
                .system(SYSTEM_PROMPT)
                .addUserMessage(buildUserPrompt(stats))
                .build();
    }

    String buildUserPrompt(SalaryStats stats) throws JsonProcessingException {
        return "New employee years of experience: %s%nSalary statistics of existing users (monthly):%n%s"
                .formatted(stats.yearsOfExperience().stripTrailingZeros().toPlainString(),
                        objectMapper.writeValueAsString(stats.forPrompt()));
    }

    private Optional<AiAdvice> extractProposal(StructuredMessage<AiSalaryProposal> response) {
        BetaStopReason stopReason = response.stopReason().orElse(null);
        if (BetaStopReason.MAX_TOKENS.equals(stopReason)) {
            BetaUsage usage = response.usage();
            log.warn("Salary advisor hit max_tokens (input tokens {}, output tokens {}); raise maxTokens or lower effort",
                    usage.inputTokens(), usage.outputTokens());
            return Optional.empty();
        }
        if (BetaStopReason.REFUSAL.equals(stopReason)) {
            log.warn("Salary advisor got no usable answer, stop reason: {}", stopReason);
            return Optional.empty();
        }
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .map(proposal -> new AiAdvice(proposal, response.model().asString()));
    }
}
