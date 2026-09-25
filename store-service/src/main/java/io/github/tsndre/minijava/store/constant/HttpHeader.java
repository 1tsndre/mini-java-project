package io.github.tsndre.minijava.store.constant;

public final class HttpHeader {

    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_SCHEME = "Bearer";
    public static final String REQUEST_ID = "X-Request-ID";

    public static final String RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    public static final String RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    public static final String RATE_LIMIT_RESET = "X-RateLimit-Reset";

    private HttpHeader() {
    }
}
