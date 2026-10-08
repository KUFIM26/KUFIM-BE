package com.example.kufim.security;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!mysql")
public class LocalRequestRateLimiter implements RequestRateLimiter {
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    @Override public long increment(String key) {
        if (counters.size() > 10000) {
            long cutoff = Instant.now().getEpochSecond() / 60 - 2;
            counters.keySet().removeIf(k -> Long.parseLong(k.substring(k.lastIndexOf(':') + 1)) < cutoff);
        }
        return counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }
}
