package io.github.tsndre.minijava.store.web.bind;

import io.github.tsndre.minijava.store.util.Uuids;
import io.github.tsndre.minijava.store.web.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

public class PathUuidResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(PathUuid.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        PathUuid annotation = parameter.getParameterAnnotation(PathUuid.class);
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) webRequest.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        String value = variables == null ? null : variables.get(annotation.value());
        return Uuids.parse(value).orElseThrow(() -> ApiException.badRequest(annotation.message()));
    }
}
