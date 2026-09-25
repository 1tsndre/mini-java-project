package io.github.tsndre.minijava.store.repository.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * A best-effort cache: a failing cache behaves like an empty one, so callers fall back to
 * the database instead of failing the request.
 */
public interface Cache {

    /** The cached value, or empty when it is missing, unreadable or the cache is unavailable. */
    <T> Optional<T> get(String key, Class<T> type);

    /** Stores the value as JSON; a zero ttl means no expiry. */
    void set(String key, Object value, Duration ttl);

    void delete(String key);

    boolean exists(String key);
}
