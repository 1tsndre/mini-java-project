package io.github.tsndre.minijava.store.web.bind;

import io.github.tsndre.minijava.common.response.Meta;
import io.github.tsndre.minijava.store.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Supplies a {@link Meta} for the response, stamped with the request ID and the time the handler started. */
public class MetaResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(Meta.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return RequestContext.meta(webRequest.getNativeRequest(HttpServletRequest.class));
    }
}
