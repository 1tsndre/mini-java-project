package io.github.tsndre.minijava.store.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.tsndre.minijava.store.constant.CacheKey;
import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.constant.HttpHeader;
import io.github.tsndre.minijava.store.constant.ValidationLimit;
import io.github.tsndre.minijava.store.repository.cache.Cache;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.web.filter.BodySizeLimitFilter;
import io.github.tsndre.minijava.store.web.filter.RecordingResponse;
import io.github.tsndre.minijava.store.web.filter.ReplayableRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Makes a request with an Idempotency-Key header to an {@link Idempotent} route safe to retry, as in
 * the IETF Idempotency-Key draft: the first request with a key is handled and its response stored
 * for the user; a retry with the same key and body gets that response again, marked with
 * Idempotent-Replayed, instead of being handled twice. The same key with another body is refused
 * with 422, and a retry while the first request is still running with 409. A 5xx response is not
 * stored, so the request can be retried. Requests without the header are handled as usual.
 *
 * <p>It runs after the {@link RouteGuard}, as the stored responses belong to the user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final String RESERVATION = IdempotencyInterceptor.class.getName() + ".reservation";

    private final Cache cache;

    /**
     * What the cache holds for an Idempotency-Key. The fingerprint is the SHA-256 of the request body,
     * so the key cannot be reused for a different request; status is 0 while the first request is
     * still being handled.
     */
    record StoredResponse(String fingerprint, int status, @JsonInclude(JsonInclude.Include.NON_EMPTY) String body) {
    }

    private record Reservation(String cacheKey, String fingerprint) {
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(Idempotent.class)) {
            return true;
        }
        String key = request.getHeader(HttpHeader.IDEMPOTENCY_KEY);
        if (key == null || key.isEmpty()) {
            return true;
        }
        if (key.length() > ValidationLimit.MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw ApiException.badRequest("Idempotency-Key must be at most %d characters"
                    .formatted(ValidationLimit.MAX_IDEMPOTENCY_KEY_LENGTH));
        }

        String fingerprint = HexFormat.of().formatHex(sha256(body(request)));
        String cacheKey = CacheKey.IDEMPOTENCY.formatted(RequestContext.userId(request), key);

        boolean reserved;
        try {
            reserved = cache.setIfAbsent(cacheKey, new StoredResponse(fingerprint, 0, ""),
                    CacheKey.TTL_IDEMPOTENCY_PENDING);
        } catch (RuntimeException e) {
            log.error("failed to reserve idempotency key", e);
            throw new InternalException("internal server error");
        }
        if (!reserved) {
            replay(response, cacheKey, fingerprint);
            return false;
        }
        request.setAttribute(RESERVATION, new Reservation(cacheKey, fingerprint));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        if (!(request.getAttribute(RESERVATION) instanceof Reservation reservation)) {
            return;
        }
        RecordingResponse recorded = WebUtils.getNativeResponse(response, RecordingResponse.class);
        // A 5xx, or an exception nothing answered, leaves the key free for the retry.
        if (ex != null || recorded == null || recorded.getStatus() >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            cache.delete(reservation.cacheKey());
            return;
        }
        cache.set(reservation.cacheKey(), new StoredResponse(reservation.fingerprint(), recorded.getStatus(),
                new String(recorded.body(), StandardCharsets.UTF_8)), CacheKey.TTL_IDEMPOTENCY);
    }

    /** IdempotencyFilter wraps every request that has the header, so its body can be read twice. */
    private static byte[] body(HttpServletRequest request) {
        ReplayableRequest replayable = WebUtils.getNativeRequest(request, ReplayableRequest.class);
        try {
            return replayable.body();
        } catch (BodySizeLimitFilter.BodyTooLargeException e) {
            throw ApiException.of(HttpStatus.CONTENT_TOO_LARGE, ErrorCode.VALIDATION, "request body too large");
        } catch (IOException e) {
            throw ApiException.badRequest("invalid request body");
        }
    }

    /** Answers a request whose key is already in the cache. */
    private void replay(HttpServletResponse response, String cacheKey, String fingerprint) throws IOException {
        StoredResponse stored = cache.get(cacheKey, StoredResponse.class).orElseThrow(() -> {
            // The key expired between the two calls, or the cache is unreachable.
            log.error("failed to read idempotent response");
            return new InternalException("internal server error");
        });

        if (!stored.fingerprint().equals(fingerprint)) {
            throw ApiException.of(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.VALIDATION,
                    "Idempotency-Key was already used with a different request");
        }
        if (stored.status() == 0) {
            throw ApiException.of(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "a request with this Idempotency-Key is still being processed");
        }
        response.setStatus(stored.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeader.IDEMPOTENT_REPLAYED, "true");
        if (stored.body() != null) {
            response.getOutputStream().write(stored.body().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static byte[] sha256(byte[] body) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(body);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
