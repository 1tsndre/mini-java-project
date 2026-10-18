package io.github.tsndre.minijava.store.constant;

import java.time.Duration;

/** Redis key formats (used with {@link String#format}) and cache lifetimes. */
public final class CacheKey {

    public static final String PRODUCT = "product:%s";
    public static final String CART = "cart:%s";
    public static final String USER = "user:%s";
    public static final String RATE_LIMIT = "rate_limit:%s:%s";
    public static final String CART_LOCK = "cart_lock:%s";
    /** Holds the user ID of a refresh token that may still be used, by jti. */
    public static final String REFRESH_TOKEN = "refresh_token:%s";

    public static final Duration TTL_PRODUCT = Duration.ofMinutes(15);
    /** Carts never expire. */
    public static final Duration TTL_CART = Duration.ZERO;

    private CacheKey() {
    }
}
