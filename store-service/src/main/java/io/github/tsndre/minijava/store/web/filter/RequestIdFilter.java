package io.github.tsndre.minijava.store.web.filter;

import io.github.tsndre.minijava.common.logging.LogKeys;
import io.github.tsndre.minijava.store.constant.HttpHeader;
import io.github.tsndre.minijava.store.web.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Gives every request an ID (the client's X-Request-ID, or a new UUID) for responses and logs. */
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HttpHeader.REQUEST_ID);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        RequestContext.setRequestId(request, requestId);
        response.setHeader(HttpHeader.REQUEST_ID, requestId);
        MDC.put(LogKeys.REQUEST_ID, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
