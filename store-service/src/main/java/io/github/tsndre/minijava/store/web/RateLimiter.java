package io.github.tsndre.minijava.store.web;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A sliding-window rate limiter on a Redis sorted set of request timestamps. */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redis;

    /** The outcome for one request; {@code resetAt} is in Unix seconds. */
    public record Decision(boolean allowed, int remaining, long resetAt) {
    }

    /** Counts the request against the key's window; empty when Redis is unavailable. */
    public Optional<Decision> allow(String key, int limit, Duration window) {
        Instant now = Instant.now();
        long nowMillis = now.toEpochMilli();
        long windowStart = nowMillis - window.toMillis();
        long resetAt = now.plus(window).getEpochSecond();

        byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
        byte[] member = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        List<Object> results;
        try {
            results = redis.executePipelined((RedisCallback<Object>) (RedisConnection connection) -> {
                connection.zSetCommands().zRemRangeByScore(rawKey, 0, windowStart);
                connection.zSetCommands().zAdd(rawKey, nowMillis, member);
                connection.zSetCommands().zCard(rawKey);
                connection.keyCommands().expire(rawKey, window.toSeconds());
                return null;
            });
        } catch (RuntimeException e) {
            return Optional.empty();
        }

        int count = ((Number) results.get(2)).intValue();
        int remaining = Math.max(limit - count, 0);
        return Optional.of(new Decision(count <= limit, remaining, resetAt));
    }
}
