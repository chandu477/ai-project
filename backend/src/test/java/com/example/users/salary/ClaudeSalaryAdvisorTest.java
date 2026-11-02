package com.example.users.salary;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaUsage;
import com.anthropic.models.beta.messages.StructuredContentBlock;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import com.anthropic.models.beta.messages.StructuredTextBlock;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClaudeSalaryAdvisorTest {

    private static Distribution distribution(long users, int median) {
        return new Distribution(users, BigDecimal.valueOf(median - 8000),
                BigDecimal.valueOf(median - 4000), BigDecimal.valueOf(median), BigDecimal.valueOf(median + 4000),
                BigDecimal.valueOf(median + 8000));
    }

    private static Band band(String from, String to, long users, int median) {
        return new Band(new BigDecimal(from), new BigDecimal(to), BigDecimal.valueOf(median + 123),
                distribution(users, median));
    }

    private static final SalaryStats STATS = new SalaryStats(new BigDecimal("2.5"), distribution(100, 80000),
            band("2.5", "3.0", 16, 61500), band("2.5", "3.0", 16, 61500),
            List.of(new ExperienceBucket(new BigDecimal("2.0"), distribution(12, 58000)),
                    new ExperienceBucket(new BigDecimal("3.0"), distribution(9, 111111))),
            new SalaryBounds(BigDecimal.valueOf(20000), BigDecimal.valueOf(145000)));

    private static final AiSalaryProposal PROPOSAL = new AiSalaryProposal(82000, 74000, 90000, "Mid-band for 7 years.");

    private AnthropicClient client;
    private ClaudeSalaryAdvisor advisor;

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AnthropicClient> providerOf(AnthropicClient client) {
        ObjectProvider<AnthropicClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }

    @BeforeEach
    void setUp() {
        client = mock(AnthropicClient.class, Answers.RETURNS_DEEP_STUBS);
        ObjectProvider<AnthropicClient> provider = providerOf(client);
        advisor = new ClaudeSalaryAdvisor(properties(4), new ObjectMapper(), provider);
    }

    private static SalaryAdvisorProperties properties(int maxConcurrentCalls) {
        return new SalaryAdvisorProperties(true, "claude-opus-5-5", "low", Duration.ofSeconds(8), 1, maxConcurrentCalls);
    }

    @SuppressWarnings("unchecked")
    private void givenResponse(BetaStopReason stopReason, AiSalaryProposal proposal) {
        StructuredMessage<AiSalaryProposal> message = mock(StructuredMessage.class);
        StructuredContentBlock<AiSalaryProposal> block = mock(StructuredContentBlock.class);
        StructuredTextBlock<AiSalaryProposal> textBlock = mock(StructuredTextBlock.class);
        when(textBlock.text()).thenReturn(proposal);
        when(block.text()).thenReturn(Optional.of(textBlock));
        when(message.content()).thenReturn(List.of(block));
        when(message.stopReason()).thenReturn(Optional.of(stopReason));
        when(message.model()).thenReturn(Model.of("claude-opus-5-5-20261101"));
        BetaUsage usage = mock(BetaUsage.class);
        when(usage.inputTokens()).thenReturn(500L);
        when(usage.outputTokens()).thenReturn(16000L);
        when(message.usage()).thenReturn(usage);
        when(client.beta().messages().create(any(StructuredMessageCreateParams.class))).thenReturn(message);
    }

    @Test
    void mapsStructuredResponse() {
        givenResponse(BetaStopReason.END_TURN, PROPOSAL);

        assertThat(advisor.propose(STATS)).contains(new AiAdvice(PROPOSAL, "claude-opus-5-5-20261101"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void requestUsesConfiguredModelAndTheFallbackBeta() {
        givenResponse(BetaStopReason.END_TURN, PROPOSAL);

        advisor.propose(STATS);

        ArgumentCaptor<StructuredMessageCreateParams<AiSalaryProposal>> captor =
                ArgumentCaptor.forClass(StructuredMessageCreateParams.class);
        verify(client.beta().messages()).create(captor.capture());
        StructuredMessageCreateParams<AiSalaryProposal> params = captor.getValue();
        assertThat(params.model().asString()).isEqualTo("claude-opus-5-5");
        assertThat(params.betas()).isPresent();
        assertThat(params.fallbacks()).isPresent();
        assertThat(params.maxTokens()).isEqualTo(16000);
    }

    private static SalaryStats statsWithAwkwardNumbers() {
        Distribution odd = new Distribution(40, BigDecimal.valueOf(60499), BigDecimal.valueOf(70500),
                BigDecimal.valueOf(80501), BigDecimal.valueOf(90499.99), BigDecimal.valueOf(99999));
        Band target = new Band(new BigDecimal("2.5"), new BigDecimal("3.0"), new BigDecimal("75500.49"), odd);
        return new SalaryStats(new BigDecimal("2.5"), odd, target, target,
                List.of(new ExperienceBucket(new BigDecimal("2.0"), odd)), STATS.bounds());
    }

    private JsonNode promptJson(SalaryStats stats) throws Exception {
        String prompt = advisor.buildUserPrompt(stats);
        return new ObjectMapper().readTree(prompt.substring(prompt.indexOf('{')));
    }

    @Test
    void promptContainsTargetBandWithOneAverageAndPercentilesElsewhere() throws Exception {
        String prompt = advisor.buildUserPrompt(STATS);

        assertThat(prompt).startsWith("New employee years of experience: 2.5");
        assertThat(prompt).contains("\"targetBand\"", "\"fromYears\":2.5", "\"toYears\":3.0", "\"average\":62000",
                "\"p10\"", "\"p25\"", "\"median\":62000", "\"p75\"", "\"p90\"", "\"neighbouringBands\"",
                "\"overall\"");
        assertThat(prompt.split("\"average\"", -1)).hasSize(2);
        assertThat(promptJson(STATS).at("/overall/average").isMissingNode()).isTrue();
        assertThat(promptJson(STATS).at("/neighbouringBands/0/salaries/average").isMissingNode()).isTrue();
        assertThat(prompt).doesNotContainIgnoringCase("username").doesNotContainIgnoringCase("min")
                .doesNotContainIgnoringCase("max").doesNotContain("bounds").doesNotContain("145000");
    }

    @Test
    void everySalaryInThePromptIsRoundedToThousands() throws Exception {
        JsonNode root = promptJson(statsWithAwkwardNumbers());

        List<JsonNode> distributions = root.findParents("median");
        assertThat(distributions).hasSize(3);
        for (JsonNode distribution : distributions) {
            for (String key : List.of("p10", "p25", "median", "p75", "p90")) {
                assertThat(distribution.get(key).decimalValue().remainder(BigDecimal.valueOf(1000)))
                        .as(key).isEqualByComparingTo(BigDecimal.ZERO);
            }
        }
        assertThat(root.at("/targetBand/average").decimalValue().remainder(BigDecimal.valueOf(1000)))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(root.at("/targetBand/average").decimalValue()).isEqualByComparingTo("76000");
        assertThat(root.at("/overall/median").decimalValue()).isEqualByComparingTo("81000");
    }

    @Test
    void promptOmitsGroupsWithFewerThanTenUsers() throws Exception {
        JsonNode root = promptJson(STATS);

        assertThat(root.at("/neighbouringBands")).hasSize(1);
        assertThat(root.findValues("users")).allSatisfy(users -> assertThat(users.asLong()).isGreaterThanOrEqualTo(10));
        assertThat(advisor.buildUserPrompt(STATS)).doesNotContain("111000", "111111");
    }

    @Test
    void fewerThanTenUsersAreNeverSent() {
        Band tiny = band("0.0", "60.5", 9, 80000);
        SalaryStats stats = new SalaryStats(new BigDecimal("2.5"), distribution(9, 80000), tiny, tiny, List.of(),
                STATS.bounds());

        assertThat(advisor.propose(stats)).isEmpty();
        verifyNoInteractions(client.beta());
    }

    @Test
    void maxTokensStopLogsUsageAndGivesEmpty() {
        givenResponse(BetaStopReason.MAX_TOKENS, PROPOSAL);

        assertThat(advisor.propose(STATS)).isEmpty();
    }

    @Test
    void saturatedAdvisorReturnsEmptyWithoutCallingClaude() throws Exception {
        ObjectProvider<AnthropicClient> provider = providerOf(client);
        ClaudeSalaryAdvisor limited = new ClaudeSalaryAdvisor(properties(1), new ObjectMapper(), provider);
        CountDownLatch inCall = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        givenResponse(BetaStopReason.END_TURN, PROPOSAL);
        when(client.beta().messages().create(any(StructuredMessageCreateParams.class))).thenAnswer(invocation -> {
            inCall.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Optional<AiAdvice>> first = executor.submit(() -> limited.propose(STATS));
            assertThat(inCall.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(limited.propose(STATS)).isEmpty();

            release.countDown();
            first.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        verify(client.beta().messages(), times(1)).create(any(StructuredMessageCreateParams.class));
    }

    @Test
    void permitIsReleasedAfterAFailedCall() {
        ClaudeSalaryAdvisor limited = new ClaudeSalaryAdvisor(properties(1), new ObjectMapper(), providerOf(client));
        when(client.beta().messages().create(any(StructuredMessageCreateParams.class)))
                .thenThrow(new AnthropicIoException("timed out"));

        assertThat(limited.propose(STATS)).isEmpty();
        assertThat(limited.propose(STATS)).isEmpty();
        verify(client.beta().messages(), times(2)).create(any(StructuredMessageCreateParams.class));
    }

    @Test
    void unexpectedRuntimeFailureGivesEmpty() {
        when(client.beta().messages().create(any(StructuredMessageCreateParams.class)))
                .thenThrow(new IllegalStateException("unexpected SDK parsing problem"));

        assertThat(advisor.propose(STATS)).isEmpty();
    }

    @Test
    void refusalGivesEmpty() {
        givenResponse(BetaStopReason.REFUSAL, PROPOSAL);

        assertThat(advisor.propose(STATS)).isEmpty();
    }

    @Test
    void ioFailureOrTimeoutGivesEmpty() {
        when(client.beta().messages().create(any(StructuredMessageCreateParams.class)))
                .thenThrow(new AnthropicIoException("timed out"));

        assertThat(advisor.propose(STATS)).isEmpty();
    }

    @Test
    void rateLimitGivesEmpty() {
        RateLimitException rateLimit = mock(RateLimitException.class);
        when(client.beta().messages().create(any(StructuredMessageCreateParams.class))).thenThrow(rateLimit);

        assertThat(advisor.propose(STATS)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void withoutClientGivesEmpty() {
        ObjectProvider<AnthropicClient> noClient = mock(ObjectProvider.class);
        ClaudeSalaryAdvisor disabled = new ClaudeSalaryAdvisor(properties(4), new ObjectMapper(), noClient);

        assertThat(disabled.propose(STATS)).isEmpty();
    }
}
