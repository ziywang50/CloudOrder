package com.highvia.apigateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {
    private final RedisTemplate<String, String> redisTemplate;
    private static final String RATE_LIMIT_LUA_SCRIPT = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local windowMs = tonumber(ARGV[2])
        local maxRequests = tonumber(ARGV[3])
        local expireSeconds = tonumber(ARGV[4])
    
        local startOfWindow = now - windowMs
    
        local counters = redis.call('HGETALL', key)
    
        local countInWindow = 0
        for i = 1, #counters, 2 do
           local timestamp = tonumber(counters[i])
           local count = tonumber(counters[i+1])
           if (timestamp >= startOfWindow) then
               countInWindow = countInWindow + count
           else
               redis.call('HDEL', key, timestamp)
           end
        end
    
        if countInWindow >= maxRequests then
            return {0, 0} --{allowed: false, remaining: 0}
        else
            redis.call('HINCRBY', key, now, 1)
            redis.call('EXPIRE', key, expireSeconds)
            local remaining = maxRequests - countInWindow - 1
            return {1, remaining} -- {allowed: true, remaining: X}
        end
    """;

    /**
     * Check if request is allowed under rate limit
     *
     * @param key Redis key (e.g., "rate_limit:user:123")
     * @param maxRequests Maximum requests allowed in window
     * @param windowSeconds Time window in seconds
     * @return true if allowed, false if rate limited
     */
    public boolean isAllowed(String key, int maxRequests, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        int expireSeconds = windowSeconds + 10;

        DefaultRedisScript<List> script = new DefaultRedisScript<>(RATE_LIMIT_LUA_SCRIPT, List.class);
        try {
            List<Long> result = redisTemplate.execute(script,
                    Collections.singletonList(key),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(maxRequests),
                    String.valueOf(expireSeconds)
            );
            if (result != null && !result.isEmpty()) {
                long allowed = result.get(0);
                if (allowed == 0) {
                    return false;
                } else {
                    return true;
                }
            }
            return true;
        } catch (Exception e) {
            log.error("Rate limit check failed for key: {}, allowing request", key, e);
            return true;
        }

    }
}
