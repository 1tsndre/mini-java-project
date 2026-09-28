package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.jwt.JwtManager;
import io.github.tsndre.minijava.common.upload.Uploader;
import io.github.tsndre.minijava.store.config.AppConfig;
import io.github.tsndre.minijava.store.config.JacksonConfig;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.grpc.PaymentStatusClient;
import io.github.tsndre.minijava.store.service.AuthService;
import io.github.tsndre.minijava.store.service.CartService;
import io.github.tsndre.minijava.store.service.CategoryService;
import io.github.tsndre.minijava.store.service.OrderService;
import io.github.tsndre.minijava.store.service.ProductService;
import io.github.tsndre.minijava.store.service.ReviewService;
import io.github.tsndre.minijava.store.service.StoreService;
import io.github.tsndre.minijava.store.web.RateLimiter;
import io.github.tsndre.minijava.store.web.RouteGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * The HTTP layer with the real filters, route guard, argument resolvers, JSON configuration and
 * error handling, and mocked services.
 */
@WebMvcTest
@Import({JacksonConfig.class, RouteGuard.class, WebMvcTestSupport.Beans.class})
abstract class WebMvcTestSupport {

    static final String JWT_SECRET = "controller-test-secret";
    static final Path UPLOAD_DIR = createUploadDir();

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtManager jwtManager;

    @MockitoBean
    RateLimiter rateLimiter;
    @MockitoBean
    AuthService authService;
    @MockitoBean
    StoreService storeService;
    @MockitoBean
    CategoryService categoryService;
    @MockitoBean
    ProductService productService;
    @MockitoBean
    CartService cartService;
    @MockitoBean
    OrderService orderService;
    @MockitoBean
    ReviewService reviewService;
    @MockitoBean
    PaymentStatusClient paymentStatusClient;

    @TestConfiguration
    static class Beans {

        @Bean
        AppConfig appConfig() {
            return AppConfig.load(new MockEnvironment()
                    .withProperty("JWT_SECRET", JWT_SECRET)
                    .withProperty("UPLOAD_DIR", UPLOAD_DIR.toString()));
        }

        @Bean
        JwtManager jwtManager(AppConfig config) {
            return new JwtManager(config.jwt().secret(), config.jwt().accessExpiry(), config.jwt().refreshExpiry());
        }

        @Bean
        Uploader uploader(AppConfig config) {
            return new Uploader(config.upload().dir(), config.upload().maxSize());
        }
    }

    private static Path createUploadDir() {
        try {
            return Files.createTempDirectory("uploads");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Sends the request as a logged-in user with the given role. */
    <B extends AbstractMockHttpServletRequestBuilder<B>> B as(UUID userId, Role role, B request) {
        String token = jwtManager.generateTokenPair(userId.toString(), "user@example.com", role.value()).accessToken();
        return request.header("Authorization", "Bearer " + token);
    }
}
