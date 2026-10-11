package ezbuild.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixed-window counters in Caffeine (fine for a single instance). */
@Component
public class RateLimiter {

    private final Map<Duration, Cache<String, AtomicInteger>> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limit, Duration window) {
        Cache<String, AtomicInteger> cache = windows.computeIfAbsent(window,
                w -> Caffeine.newBuilder().expireAfterWrite(w).maximumSize(100_000).build());
        AtomicInteger counter = cache.get(key, k -> new AtomicInteger());
        return counter.incrementAndGet() <= limit;
    }
}
