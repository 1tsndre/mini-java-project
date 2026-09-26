package io.github.tsndre.minijava.store.web.filter;

import io.github.tsndre.minijava.common.response.ApiError;
import io.github.tsndre.minijava.common.response.ApiResponse;
import io.github.tsndre.minijava.store.config.GoDuration;
import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.web.RequestContext;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Answers 504 when a request takes longer than the request timeout. The rest of the chain runs on
 * its own (virtual) thread with a buffered response, like the Go handler goroutine: if it is not
 * done in time and has not started its response, the client gets the 504 right away and whatever
 * the handler writes afterwards is discarded. A handler that already started responding is waited
 * for, so a response is never cut in half.
 */
@Slf4j
public class TimeoutFilter extends OncePerRequestFilter {

    private final Duration timeout;
    private final JsonMapper jsonMapper;
    private final ExecutorService handlers = Executors.newVirtualThreadPerTaskExecutor();

    public TimeoutFilter(Duration timeout, JsonMapper jsonMapper) {
        this.timeout = timeout;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        BufferedResponse buffered = new BufferedResponse(response);
        Map<String, String> logContext = MDC.getCopyOfContextMap();

        CompletableFuture<Void> handler = CompletableFuture.runAsync(() -> {
            if (logContext != null) {
                MDC.setContextMap(logContext);
            }
            try {
                chain.doFilter(request, buffered);
            } catch (IOException | ServletException e) {
                throw new HandlerFailure(e);
            } finally {
                MDC.clear();
            }
        }, handlers);

        try {
            handler.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            if (buffered.timeOut()) {
                respondTimedOut(request, response, handler);
                return;
            }
            // The response is already on its way; let the handler finish it.
            await(handler);
        } catch (ExecutionException e) {
            throw unwrap(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServletException(e);
        }
        buffered.copyTo(response);
    }

    private void respondTimedOut(HttpServletRequest request, HttpServletResponse response,
                                 CompletableFuture<Void> handler) throws IOException {
        log.atError()
                .addKeyValue("duration", GoDuration.format(timeout))
                .addKeyValue("path", request.getRequestURI())
                .log("request timeout");

        // Keep the request alive for the handler, which may still be using it.
        AsyncContext async = request.startAsync();
        async.setTimeout(0);

        ApiResponse body = new ApiResponse(null, RequestContext.meta(request),
                List.of(ApiError.of(ErrorCode.TIMEOUT.value(), "request timed out")));
        byte[] json = (jsonMapper.writeValueAsString(body) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setContentLength(json.length);
        response.getOutputStream().write(json);
        response.flushBuffer();

        handler.whenComplete((result, error) -> async.complete());
    }

    private static void await(CompletableFuture<Void> handler) throws IOException, ServletException {
        try {
            handler.get();
        } catch (ExecutionException e) {
            throw unwrap(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServletException(e);
        }
    }

    private static ServletException unwrap(ExecutionException e) throws IOException {
        Throwable cause = e.getCause();
        if (cause instanceof HandlerFailure failure) {
            cause = failure.getCause();
        }
        if (cause instanceof IOException io) {
            throw io;
        }
        if (cause instanceof ServletException servlet) {
            return servlet;
        }
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new ServletException(cause);
    }

    @Override
    public void destroy() {
        handlers.shutdown();
    }

    /** Carries a checked exception out of the handler thread. */
    private static final class HandlerFailure extends RuntimeException {

        HandlerFailure(Exception cause) {
            super(cause);
        }
    }
}
