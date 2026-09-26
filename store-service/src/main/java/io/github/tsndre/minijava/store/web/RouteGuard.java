package io.github.tsndre.minijava.store.web;

import io.github.tsndre.minijava.common.jwt.Claims;
import io.github.tsndre.minijava.common.jwt.JwtManager;
import io.github.tsndre.minijava.common.jwt.TokenType;
import io.github.tsndre.minijava.common.logging.LogKeys;
import io.github.tsndre.minijava.store.config.AppConfig;
import io.github.tsndre.minijava.store.constant.CacheKey;
import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.constant.HttpHeader;
import io.github.tsndre.minijava.store.constant.RateLimitKey;
import io.github.tsndre.minijava.store.constant.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Applies a route's {@link Authenticated}, {@link RequireRole} and {@link RateLimited} rules, in
 * that order, before the controller runs.
 */
@Component
@RequiredArgsConstructor
public class RouteGuard implements HandlerInterceptor {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final JwtManager jwtManager;
    private final RateLimiter rateLimiter;
    private final AppConfig config;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // The Go router has no OPTIONS routes, so its catch-all answers them with a 404.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            throw ApiException.notFound();
        }
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        if (method.hasMethodAnnotation(Authenticated.class)) {
            authenticate(request);
        }
        RequireRole requireRole = method.getMethodAnnotation(RequireRole.class);
        if (requireRole != null) {
            requireRole(request, requireRole.value());
        }
        RateLimited rateLimited = method.getMethodAnnotation(RateLimited.class);
        if (rateLimited != null) {
            for (RateLimitKey key : rateLimited.value()) {
                limit(request, response, key);
            }
        }
        return true;
    }

    private void authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeader.AUTHORIZATION);
        if (authHeader == null || authHeader.isEmpty()) {
            throw ApiException.unauthorized("missing authorization header");
        }

        String[] parts = authHeader.split(" ", 2);
        if (parts.length != 2 || !parts[0].equals(HttpHeader.BEARER_SCHEME)) {
            throw ApiException.unauthorized("invalid authorization format");
        }

        Claims claims;
        try {
            claims = jwtManager.validateToken(parts[1]);
        } catch (RuntimeException e) {
            throw ApiException.unauthorized("invalid or expired token");
        }

        if (!TokenType.ACCESS.value().equals(claims.type())) {
            throw ApiException.unauthorized("invalid token type");
        }

        request.setAttribute(RequestContext.USER_ID, claims.userId());
        request.setAttribute(RequestContext.EMAIL, claims.email());
        request.setAttribute(RequestContext.ROLE, claims.role());
        if (!claims.userId().isEmpty()) {
            MDC.put(LogKeys.USER_ID, claims.userId());
        }
    }

    private static void requireRole(HttpServletRequest request, Role[] roles) {
        if (!(request.getAttribute(RequestContext.ROLE) instanceof String role)) {
            throw ApiException.of(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "forbidden");
        }
        if (Arrays.stream(roles).map(Role::value).noneMatch(role::equals)) {
            throw ApiException.of(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "insufficient permissions");
        }
    }

    private void limit(HttpServletRequest request, HttpServletResponse response, RateLimitKey key) {
        int limit = limitOf(key);

        String identifier = request.getRemoteAddr();
        String userId = RequestContext.userId(request);
        if (!userId.isEmpty()) {
            identifier = userId;
        }

        Optional<RateLimiter.Decision> decision =
                rateLimiter.allow(CacheKey.RATE_LIMIT.formatted(key.value(), identifier), limit, RATE_WINDOW);
        if (decision.isEmpty()) {
            // If Redis fails, allow the request.
            return;
        }

        response.setHeader(HttpHeader.RATE_LIMIT_LIMIT, Integer.toString(limit));
        response.setHeader(HttpHeader.RATE_LIMIT_REMAINING, Integer.toString(decision.get().remaining()));
        response.setHeader(HttpHeader.RATE_LIMIT_RESET, Long.toString(decision.get().resetAt()));

        if (!decision.get().allowed()) {
            throw ApiException.of(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, "rate limit exceeded");
        }
    }

    private int limitOf(RateLimitKey key) {
        AppConfig.Rate rate = config.rate();
        return switch (key) {
            case PUBLIC -> rate.publicLimit();
            case AUTH -> rate.authLimit();
            case LOGIN -> rate.loginLimit();
        };
    }
}
