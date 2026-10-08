package com.example.kufim.security;

import com.example.kufim.common.exception.ContractException;
import java.time.Instant;
import java.util.Map;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;

public interface RequestRateLimiter {
    long increment(String key);
    default void check(String category, String identity, int limit, String errorCode) {
        long now = Instant.now().getEpochSecond();
        byte[] digest;
        try { digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
        long value = increment(category + ":" + HexFormat.of().formatHex(digest) + ":" + now / 60);
        if (value > limit) throw new ContractException(HttpStatus.TOO_MANY_REQUESTS, errorCode,
                "잠시 후 다시 시도해 주세요.", Map.of("retryAfterSeconds", 60 - now % 60));
    }
}
