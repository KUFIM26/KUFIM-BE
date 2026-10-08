package com.example.kufim.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("mysql")
@RequiredArgsConstructor
public class RedisRequestRateLimiter implements RequestRateLimiter {
    private final StringRedisTemplate redis;
    @Override public long increment(String key) {
        String redisKey = "kufim:rate:" + key;
        Long count = redis.opsForValue().increment(redisKey);
        if (count != null && count == 1) redis.expire(redisKey, Duration.ofMinutes(2));
        return count == null ? 0 : count;
    }
}
