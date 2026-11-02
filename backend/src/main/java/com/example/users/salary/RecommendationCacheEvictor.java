package com.example.users.salary;

import com.example.users.user.UserCreatedEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * New users change the statistics, so cached recommendations are dropped. A plain listener is right
 * here: UserService publishes the event after its insert has committed and runs without a transaction,
 * so there is no transaction phase to wait for.
 */
@Component
public class RecommendationCacheEvictor {

    private final CacheManager cacheManager;

    public RecommendationCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @EventListener
    public void onUserCreated(UserCreatedEvent event) {
        Cache cache = cacheManager.getCache(SalaryRecommendationService.CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }
}
