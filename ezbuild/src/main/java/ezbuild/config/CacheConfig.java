package ezbuild.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Caffeine caches. Never cache anything personal (carts, orders, chats). */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS = "products";
    public static final String FACETS = "catalog-facets";
    public static final String FEATURED = "featured";
    public static final String BUILD_PLANS = "build-plans";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(PRODUCTS, build(Duration.ofMinutes(10)));
        manager.registerCustomCache(FACETS, build(Duration.ofMinutes(10)));
        manager.registerCustomCache(FEATURED, build(Duration.ofMinutes(5)));
        manager.registerCustomCache(BUILD_PLANS, build(Duration.ofMinutes(5)));
        return manager;
    }

    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> build(Duration ttl) {
        return Caffeine.newBuilder().maximumSize(500).expireAfterWrite(ttl).recordStats().build();
    }
}
