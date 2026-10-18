package io.github.tsndre.minijava.store.repository.cache;

import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** An in-memory cache for tests. It stores JSON like the Redis cache, but ignores expiry. */
public final class InMemoryCache implements Cache {

    private final Map<String, String> data = new ConcurrentHashMap<>();
    private final JsonMapper json;

    public InMemoryCache(JsonMapper json) {
        this.json = json;
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        String value = data.get(key);
        return value == null ? Optional.empty() : Optional.of(json.readValue(value, type));
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        data.put(key, json.writeValueAsString(value));
    }

    @Override
    public void delete(String key) {
        data.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return data.containsKey(key);
    }

    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        return data.putIfAbsent(key, json.writeValueAsString(value)) == null;
    }

    /** The stored JSON, as Redis would hold it. */
    public Optional<String> raw(String key) {
        return Optional.ofNullable(data.get(key));
    }

    public void clear() {
        data.clear();
    }
}
