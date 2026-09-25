package io.github.tsndre.minijava.store.constant;

/** Rate limit buckets; each has its own limit per minute (RATE_LIMIT_*). */
public enum RateLimitKey {

    PUBLIC("public"),
    AUTH("auth"),
    LOGIN("login");

    private final String value;

    RateLimitKey(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
