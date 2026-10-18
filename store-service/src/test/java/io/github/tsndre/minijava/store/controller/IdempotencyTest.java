package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.service.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checkout with an Idempotency-Key, through the real filters, route guard and interceptor. */
class IdempotencyTest extends WebMvcTestSupport {

    private static final String BODY = "{\"shipping_address\":\"Jl. Test 1\"}";

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void emptyCacheAndOrders() {
        cache.clear();
        when(orderService.checkout(any(), any())).thenReturn(List.of());
    }

    private MockHttpServletRequestBuilder checkout(UUID user, String key, String body) {
        MockHttpServletRequestBuilder request = as(user, Role.BUYER, post("/api/v1/orders").content(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    @Test
    void aRetryReplaysTheFirstResponseInsteadOfCheckingOutAgain() throws Exception {
        MockHttpServletResponse first = send(checkout(userId, "k1", BODY));
        MockHttpServletResponse retry = send(checkout(userId, "k1", BODY));

        verify(orderService, times(1)).checkout(userId, "Jl. Test 1");
        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(first.getHeader("Idempotent-Replayed")).isNull();
        assertThat(retry.getStatus()).isEqualTo(201);
        assertThat(retry.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(retry.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getContentType()).isEqualTo("application/json");
    }

    @Test
    void theStoredResponseIsTheJsonTheGoServiceStores() throws Exception {
        MockHttpServletResponse first = send(checkout(userId, "k1", BODY));

        String expected = "{\"fingerprint\":\"" + sha256(BODY) + "\",\"status\":201,\"body\":"
                + jsonString(first.getContentAsString()) + "}";
        assertThat(cache.raw("idempotency:" + userId + ":k1")).hasValue(expected);
    }

    @Test
    void a4xxResponseIsReplayedToo() throws Exception {
        when(orderService.checkout(any(), any())).thenThrow(new ValidationException("cart is empty"));

        send(checkout(userId, "k1", BODY));
        MockHttpServletResponse retry = send(checkout(userId, "k1", BODY));

        verify(orderService, times(1)).checkout(any(), any());
        assertThat(retry.getStatus()).isEqualTo(400);
        assertThat(retry.getHeader("Idempotent-Replayed")).isEqualTo("true");
    }

    @Test
    void theSameKeyWithAnotherBodyIsRefused() throws Exception {
        send(checkout(userId, "k1", BODY));

        mvc.perform(checkout(userId, "k1", "{\"shipping_address\":\"Elsewhere\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.VALIDATION.value()))
                .andExpect(jsonPath("$.errors[0].message").value("Idempotency-Key was already used with a different request"));
        verify(orderService, times(1)).checkout(any(), any());
    }

    @Test
    void aRetryWhileTheFirstRequestIsRunningIsRefused() throws Exception {
        // What the first request leaves in the cache until it finishes.
        cache.set("idempotency:" + userId + ":k1", Map.of("fingerprint", sha256(BODY), "status", 0), Duration.ZERO);

        mvc.perform(checkout(userId, "k1", BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.CONFLICT.value()))
                .andExpect(jsonPath("$.errors[0].message").value("a request with this Idempotency-Key is still being processed"));
        verify(orderService, never()).checkout(any(), any());
    }

    @Test
    void a5xxResponseIsNotStoredSoTheRequestCanBeRetried() throws Exception {
        when(orderService.checkout(any(), any())).thenThrow(new IllegalStateException("boom"));

        send(checkout(userId, "k1", BODY));
        MockHttpServletResponse retry = send(checkout(userId, "k1", BODY));

        verify(orderService, times(2)).checkout(any(), any());
        assertThat(retry.getStatus()).isEqualTo(500);
        assertThat(retry.getHeader("Idempotent-Replayed")).isNull();
        assertThat(cache.exists("idempotency:" + userId + ":k1")).isFalse();
    }

    @Test
    void keysArePerUser() throws Exception {
        UUID otherUser = UUID.randomUUID();

        send(checkout(userId, "k1", BODY));
        send(checkout(otherUser, "k1", BODY));

        verify(orderService).checkout(eq(userId), any());
        verify(orderService).checkout(eq(otherUser), any());
    }

    @Test
    void withoutTheHeaderEveryRequestIsHandled() throws Exception {
        send(checkout(userId, null, BODY));
        send(checkout(userId, null, BODY));

        verify(orderService, times(2)).checkout(any(), any());
    }

    @Test
    void anOverlongKeyIsRefused() throws Exception {
        mvc.perform(checkout(userId, "k".repeat(256), BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Idempotency-Key must be at most 255 characters"));
        verify(orderService, never()).checkout(any(), any());
    }

    @Test
    void aBodyOverTheLimitIsRefused() throws Exception {
        String body = "{\"shipping_address\":\"" + "x".repeat(7 << 20) + "\"}";

        mvc.perform(checkout(userId, "k1", body))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.errors[0].message").value("request body too large"));
        verify(orderService, never()).checkout(any(), any());
    }

    @Test
    void theRouteGuardRunsFirst() throws Exception {
        mvc.perform(post("/api/v1/orders").content(BODY).header("Idempotency-Key", "k1"))
                .andExpect(status().isUnauthorized());

        assertThat(cache.exists("idempotency::k1")).isFalse();
    }

    private static String sha256(String body) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String jsonString(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
