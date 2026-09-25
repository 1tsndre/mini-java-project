package io.github.tsndre.minijava.store.repository.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisCache implements Cache {

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? Optional.empty() : Optional.of(jsonMapper.readValue(value, type));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        try {
            String json = jsonMapper.writeValueAsString(value);
            if (ttl.isZero()) {
                redis.opsForValue().set(key, json);
            } else {
                redis.opsForValue().set(key, json, ttl);
            }
        } catch (RuntimeException ignored) {
            // Best effort, like a failed SET in the Go service: the next read goes to the database.
        }
    }

    @Override
    public void delete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException ignored) {
            // Best effort; the entry expires on its own.
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
