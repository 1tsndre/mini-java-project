package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.web.RateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RouteGuardTest extends WebMvcTestSupport {

    private final UUID userId = UUID.randomUUID();

    private void expectUnauthorized(String authorization, String message) throws Exception {
        var request = get("/api/v1/cart");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.errors[0].message").value(message));
    }

    @Test
    void missingAuthorizationHeader() throws Exception {
        expectUnauthorized(null, "missing authorization header");
    }

    @Test
    void invalidAuthorizationFormat() throws Exception {
        expectUnauthorized("Token abc", "invalid authorization format");
        expectUnauthorized("Bearer", "invalid authorization format");
        expectUnauthorized("bearer abc", "invalid authorization format");
    }

    @Test
    void invalidOrExpiredToken() throws Exception {
        expectUnauthorized("Bearer not-a-token", "invalid or expired token");
    }

    @Test
    void aRefreshTokenIsNotAnAccessToken() throws Exception {
        String refresh = jwtManager.generateTokenPair(userId.toString(), "x@y.co", Role.BUYER.value()).refreshToken();
        expectUnauthorized("Bearer " + refresh, "invalid token type");
    }

    @Test
    void wrongRoleIsForbidden() throws Exception {
        mvc.perform(as(userId, Role.SELLER, get("/api/v1/cart")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.FORBIDDEN.value()))
                .andExpect(jsonPath("$.errors[0].message").value("insufficient permissions"));
    }

    @Test
    void authenticationRunsBeforeRateLimiting() throws Exception {
        mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
        verify(rateLimiter, never()).allow(any(), anyInt(), any());
    }

    @Test
    void rateLimitHeadersAndTheLimitPerUser() throws Exception {
        when(rateLimiter.allow(eq("rate_limit:auth:" + userId), eq(120), eq(Duration.ofMinutes(1))))
                .thenReturn(Optional.of(new RateLimiter.Decision(true, 119, 1_800_000_000L)));

        mvc.perform(as(userId, Role.BUYER, get("/api/v1/cart")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "120"))
                .andExpect(header().string("X-RateLimit-Remaining", "119"))
                .andExpect(header().string("X-RateLimit-Reset", "1800000000"));
    }

    @Test
    void rateLimitExceeded() throws Exception {
        when(rateLimiter.allow(eq("rate_limit:login:127.0.0.1"), eq(10), any()))
                .thenReturn(Optional.of(new RateLimiter.Decision(false, 0, 1_800_000_000L)));

        mvc.perform(post("/api/v1/auth/login").content("{\"email\":\"a@b.co\",\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RATE_LIMITED.value()))
                .andExpect(jsonPath("$.errors[0].message").value("rate limit exceeded"));
    }

    /** Login counts against both the login and the public limit; the later one sets the headers. */
    @Test
    void loginIsLimitedByTheLoginAndThePublicLimit() throws Exception {
        when(rateLimiter.allow(eq("rate_limit:login:127.0.0.1"), eq(10), any()))
                .thenReturn(Optional.of(new RateLimiter.Decision(true, 9, 1L)));
        when(rateLimiter.allow(eq("rate_limit:public:127.0.0.1"), eq(60), any()))
                .thenReturn(Optional.of(new RateLimiter.Decision(true, 59, 1L)));

        mvc.perform(post("/api/v1/auth/login").content("{\"email\":\"a@b.co\",\"password\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "60"))
                .andExpect(header().string("X-RateLimit-Remaining", "59"));
    }

    @Test
    void withRedisDownRequestsAreAllowedWithoutHeaders() throws Exception {
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-RateLimit-Limit"));
    }
}
