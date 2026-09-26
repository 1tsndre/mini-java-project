package io.github.tsndre.minijava.store.web.bind;

import io.github.tsndre.minijava.store.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class JsonBodyResolver implements HandlerMethodArgumentResolver {

    private static final String INVALID_BODY = "invalid request body";

    private final JsonMapper jsonMapper;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(JsonBody.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Class<?> type = parameter.getParameterType();
        // A reader replaces invalid UTF-8 instead of failing, as Go's decoder does.
        try (Reader body = new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8);
             JsonParser parser = jsonMapper.createParser(body)) {
            JsonToken first = parser.nextToken();
            if (first == null) {
                throw ApiException.badRequest(INVALID_BODY);
            }
            Object value = jsonMapper.readerFor(type).readValue(parser);
            // A JSON null leaves every field at its zero value.
            return value != null ? value : jsonMapper.readValue("{}", type);
        } catch (JacksonException | IOException e) {
            throw ApiException.badRequest(INVALID_BODY);
        }
    }
}
