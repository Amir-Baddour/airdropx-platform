package com.airdropx.claim;

import com.airdropx.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window per-IP limiter for the unauthenticated claim endpoints, counted in Redis so it holds
 * across multiple backend instances. Deliberately simple: the public surface is two endpoints, and a
 * fixed window is easy to reason about. The IP is the servlet remote address — behind a reverse proxy,
 * enable Spring's forward-headers handling (server.forward-headers-strategy) so this is the client IP.
 */
@Component
class ClaimRateLimiter {

    private final StringRedisTemplate redis;
    private final int limitPerMinute;

    ClaimRateLimiter(StringRedisTemplate redis, @Value("${airdropx.claims.rate-limit-per-minute}") int limitPerMinute) {
        this.redis = redis;
        this.limitPerMinute = limitPerMinute;
    }

    void check(String ip) {
        long window = Instant.now().getEpochSecond() / 60;
        String key = "airdropx:rl:claims:" + ip + ":" + window;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofMinutes(2));
        }
        if (count != null && count > limitPerMinute) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many requests from this address — try again in a minute");
        }
    }
}
