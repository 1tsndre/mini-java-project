package io.github.tsndre.minijava.store.web;

import io.github.tsndre.minijava.common.response.Meta;
import jakarta.servlet.http.HttpServletRequest;

/** Per-request values set by the filters and the route guard, kept as request attributes. */
public final class RequestContext {

    private static final String PREFIX = RequestContext.class.getName() + ".";
    static final String REQUEST_ID = PREFIX + "requestId";
    static final String USER_ID = PREFIX + "userId";
    static final String EMAIL = PREFIX + "email";
    static final String ROLE = PREFIX + "role";

    private RequestContext() {
    }

    public static void setRequestId(HttpServletRequest request, String requestId) {
        request.setAttribute(REQUEST_ID, requestId);
    }

    public static String requestId(HttpServletRequest request) {
        return attribute(request, REQUEST_ID);
    }

    /** The authenticated user's ID as found in the token, or "" on a public route. */
    public static String userId(HttpServletRequest request) {
        return attribute(request, USER_ID);
    }

    public static Meta meta(HttpServletRequest request) {
        return Meta.of(requestId(request));
    }

    private static String attribute(HttpServletRequest request, String name) {
        return request.getAttribute(name) instanceof String value ? value : "";
    }
}
