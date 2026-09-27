package io.github.tsndre.minijava.payment.config;

import io.github.tsndre.minijava.common.constant.Env;
import org.springframework.core.env.PropertyResolver;

public record AppConfig(String env, String lookupdAddr, String nsqdAddr, String grpcPort) {

    public static AppConfig load(PropertyResolver resolver) {
        return new AppConfig(
                string(resolver, "APP_ENV", Env.PRODUCTION),
                string(resolver, "NSQ_LOOKUPD_ADDR", "localhost:4161"),
                string(resolver, "NSQD_ADDR", "localhost:4150"),
                string(resolver, "PAYMENT_GRPC_PORT", "50051"));
    }

    /** A blank value falls back to the default like an unset one. */
    private static String string(PropertyResolver resolver, String key, String defaultValue) {
        String value = resolver.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
