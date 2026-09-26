package io.github.tsndre.minijava.store.config;

import io.github.tsndre.minijava.store.web.RouteGuard;
import io.github.tsndre.minijava.store.web.bind.CurrentUserIdResolver;
import io.github.tsndre.minijava.store.web.bind.JsonBodyResolver;
import io.github.tsndre.minijava.store.web.bind.MetaResolver;
import io.github.tsndre.minijava.store.web.bind.PathUuidResolver;
import io.github.tsndre.minijava.store.web.filter.AccessLogFilter;
import io.github.tsndre.minijava.store.web.filter.BodySizeLimitFilter;
import io.github.tsndre.minijava.store.web.filter.RequestIdFilter;
import io.github.tsndre.minijava.store.web.filter.TimeoutFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.MultipartConfigElement;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * The HTTP pipeline, in the Go router's order: request ID, access log, body size limit, request
 * timeout, then the route's auth, role and rate-limit rules.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private static final long MULTIPART_HEADROOM_BYTES = 1L << 20;

    private final AppConfig config;
    private final RouteGuard routeGuard;
    private final JsonMapper jsonMapper;

    /** Uploads are the largest bodies accepted; leave headroom for the multipart encoding. */
    private long maxBodyBytes() {
        return config.upload().maxSize() + MULTIPART_HEADROOM_BYTES;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(routeGuard);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new MetaResolver());
        resolvers.add(new CurrentUserIdResolver());
        resolvers.add(new PathUuidResolver());
        resolvers.add(new JsonBodyResolver(jsonMapper));
    }

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        return register(new RequestIdFilter(), 1);
    }

    @Bean
    FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
        return register(new AccessLogFilter(), 2);
    }

    @Bean
    FilterRegistrationBean<BodySizeLimitFilter> bodySizeLimitFilter() {
        return register(new BodySizeLimitFilter(maxBodyBytes()), 3);
    }

    @Bean
    FilterRegistrationBean<TimeoutFilter> timeoutFilter() {
        return register(new TimeoutFilter(config.app().requestTimeout(), jsonMapper), 4);
    }

    /** Multipart bodies are parsed by Tomcat, so they get the body size limit here. */
    @Bean
    MultipartConfigElement multipartConfigElement() {
        return new MultipartConfigElement("", -1, maxBodyBytes(), 0);
    }

    private static <T extends Filter> FilterRegistrationBean<T> register(T filter, int position) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + position);
        registration.setAsyncSupported(true);
        return registration;
    }
}
