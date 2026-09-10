package com.omar.vendora.identity.service;

import com.omar.vendora.common.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final long windowSeconds;
    private final Map<String, Deque<Long>> attemptsPerKey = new ConcurrentHashMap<>();

    public LoginRateLimiter(
        @Value("${security.rate-limit.login.max-attempts:10}") int maxAttempts,
        @Value("${security.rate-limit.login.window-seconds:60}") long windowSeconds
    ) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    public synchronized void acquire(String key) {
        long now = Instant.now().getEpochSecond();
        long windowStart = now - windowSeconds;

        Deque<Long> timestamps = attemptsPerKey.computeIfAbsent(key, k -> new ArrayDeque<>());

        // Evict expired attempts
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxAttempts) {
            throw new RateLimitExceededException();
        }

        timestamps.addLast(now);
    }

    public synchronized void reset() {
        attemptsPerKey.clear();
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }
}
