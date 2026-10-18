package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.constant.CacheKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Keeps each session's user ID as a JSON string, the way the Go service's cache stores it. */
@Repository
@RequiredArgsConstructor
public class RedisRefreshTokenRepository implements RefreshTokenRepository {

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    @Override
    public void save(String tokenId, UUID userId, Duration ttl) {
        redis.opsForValue().set(key(tokenId), jsonMapper.writeValueAsString(userId.toString()), ttl);
    }

    @Override
    public Optional<UUID> consume(String tokenId) {
        // GETDEL, so of two requests with the same token only one gets the session.
        String value = redis.opsForValue().getAndDelete(key(tokenId));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(jsonMapper.readValue(value, String.class)));
    }

    @Override
    public void revoke(String tokenId) {
        redis.delete(key(tokenId));
    }

    private static String key(String tokenId) {
        return CacheKey.REFRESH_TOKEN.formatted(tokenId);
    }
}
