package io.github.tsndre.minijava.store.web.filter;

import io.github.tsndre.minijava.store.constant.HttpHeader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Lets the idempotency interceptor read the body of a request with an Idempotency-Key and keep a
 * copy of its response. The interceptor does the rest, after the route's auth and rate limits.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HttpHeader.IDEMPOTENCY_KEY);
        if (key == null || key.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new ReplayableRequest(request), new RecordingResponse(response));
    }
}
