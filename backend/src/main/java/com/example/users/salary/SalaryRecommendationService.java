package com.example.users.salary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SalaryRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(SalaryRecommendationService.class);

    public static final String CACHE_NAME = "salaryRecommendations";

    private static final Duration FOLLOWER_MARGIN = Duration.ofSeconds(5);
    private static final int MAX_RATIONALE_LENGTH = 600;
    private static final BigDecimal LOWER_FACTOR = new BigDecimal("0.5");
    private static final BigDecimal UPPER_FACTOR = new BigDecimal("1.5");

    private final SalaryStatsService statsService;
    private final ClaudeSalaryAdvisor advisor;
    private final CacheManager cacheManager;
    private final Duration followerWait;
    private final Map<Integer, CompletableFuture<SalaryRecommendation>> inFlight = new ConcurrentHashMap<>();

    public SalaryRecommendationService(SalaryStatsService statsService, ClaudeSalaryAdvisor advisor,
                                       CacheManager cacheManager, SalaryAdvisorProperties properties) {
        this.statsService = statsService;
        this.advisor = advisor;
        this.cacheManager = cacheManager;
        this.followerWait = properties.timeout().multipliedBy(properties.maxRetries() + 1L).plus(FOLLOWER_MARGIN);
    }

    /**
     * Only AI answers are cached (per experience, until a user is created), so a short Claude outage
     * doesn't stick. Concurrent identical requests share one computation: the first caller computes,
     * the others wait for its result. A request racing a user creation can still cache a result
     * computed from the old data; accepted for a single instance.
     */
    public SalaryRecommendation recommend(BigDecimal yearsOfExperience) {
        return recommend(SalaryStatsService.toStep(yearsOfExperience));
    }

    /** Keyed by the half-year step (years x 2), so 7, 7.0 and 7.00 share one cache entry. */
    private SalaryRecommendation recommend(int yearsOfExperience) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        SalaryRecommendation cached = cache.get(yearsOfExperience, SalaryRecommendation.class);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<SalaryRecommendation> mine = new CompletableFuture<>();
        CompletableFuture<SalaryRecommendation> running = inFlight.putIfAbsent(yearsOfExperience, mine);
        if (running != null) {
            return await(running, yearsOfExperience);
        }
        try {
            SalaryRecommendation result = computeUnlessCached(yearsOfExperience, cache);
            mine.complete(result);
            return result;
        } catch (RuntimeException ex) {
            mine.completeExceptionally(ex);
            throw ex;
        } finally {
            // An Error (or anything else unexpected) must not leave followers waiting.
            mine.cancel(false);
            inFlight.remove(yearsOfExperience, mine);
        }
    }

    /**
     * Waits for the leader, at most the advisor's worst-case duration plus a margin. If the leader
     * failed in an unexpected way or took too long, the follower answers with the statistical estimate.
     */
    private SalaryRecommendation await(CompletableFuture<SalaryRecommendation> running, int yearsOfExperience) {
        try {
            return running.get(followerWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            return statisticalEstimate(yearsOfExperience);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return statisticalEstimate(yearsOfExperience);
        } catch (TimeoutException | CancellationException ex) {
            log.warn("Waiting for a shared salary recommendation failed ({}), using the statistical estimate",
                    ex.getClass().getSimpleName());
            return statisticalEstimate(yearsOfExperience);
        }
    }

    private SalaryRecommendation statisticalEstimate(int yearsOfExperience) {
        return SalaryRecommendation.statistical(statsService.computeStats(yearsOfExperience));
    }

    /** Re-checks the cache: another caller may have finished between our first check and becoming leader. */
    private SalaryRecommendation computeUnlessCached(int yearsOfExperience, Cache cache) {
        SalaryRecommendation cached = cache.get(yearsOfExperience, SalaryRecommendation.class);
        if (cached != null) {
            return cached;
        }
        SalaryRecommendation result = compute(yearsOfExperience);
        if (result.source() == RecommendationSource.AI) {
            cache.put(yearsOfExperience, result);
        }
        return result;
    }

    private SalaryRecommendation compute(int yearsOfExperience) {
        SalaryStats stats = statsService.computeStats(yearsOfExperience);
        return advisor.propose(stats)
                .flatMap(proposal -> toValidRecommendation(proposal, stats))
                .orElseGet(() -> SalaryRecommendation.statistical(stats));
    }

    private Optional<SalaryRecommendation> toValidRecommendation(AiAdvice advice, SalaryStats stats) {
        AiSalaryProposal proposal = advice.proposal();
        if (!isPlausible(proposal, stats.bounds())) {
            return Optional.empty();
        }
        return Optional.of(new SalaryRecommendation(
                BigDecimal.valueOf(proposal.recommendedMonthlySalary()).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(proposal.rangeLow()).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(proposal.rangeHigh()).setScale(2, RoundingMode.HALF_UP),
                proposal.rationale().strip(), RecommendationSource.AI, stats.promptBand().salaries().users(),
                stats.promptBand().fromYears(), stats.promptBand().toYears(), advice.model()));
    }

    private static boolean isPlausible(AiSalaryProposal proposal, SalaryBounds bounds) {
        double minAllowed = bounds.min().multiply(LOWER_FACTOR).doubleValue();
        double maxAllowed = bounds.max().multiply(UPPER_FACTOR).doubleValue();
        double low = proposal.rangeLow();
        double recommended = proposal.recommendedMonthlySalary();
        double high = proposal.rangeHigh();
        return hasUsableRationale(proposal.rationale())
                && Double.isFinite(low) && Double.isFinite(recommended) && Double.isFinite(high)
                && low > 0 && low <= recommended && recommended <= high
                && low >= minAllowed && high <= maxAllowed;
    }

    private static boolean hasUsableRationale(String rationale) {
        return rationale != null && !rationale.isBlank() && rationale.strip().length() <= MAX_RATIONALE_LENGTH;
    }
}
