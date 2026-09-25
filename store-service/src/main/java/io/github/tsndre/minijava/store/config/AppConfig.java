package io.github.tsndre.minijava.store.config;

import io.github.tsndre.minijava.common.constant.Env;
import org.springframework.core.env.PropertyResolver;

import java.time.Duration;

public record AppConfig(App app, Db db, Redis redis, Nsq nsq, Jwt jwt, Rate rate, Upload upload, Payment payment) {

    static final String INSECURE_JWT_SECRET = "your-super-secret-key-change-this";

    public record App(String port, String env, Duration readTimeout, Duration writeTimeout,
                      Duration idleTimeout, Duration shutdownTimeout, Duration requestTimeout) {
    }

    public record Db(String host, String port, String user, String password, String name, String sslMode) {

        public String jdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + name + "?sslmode=" + sslMode;
        }
    }

    public record Redis(String host, String port, String password, int db) {

        public String addr() {
            return host + ":" + port;
        }
    }

    public record Nsq(String lookupdAddr, String nsqdAddr) {
    }

    public record Jwt(String secret, Duration accessExpiry, Duration refreshExpiry) {
    }

    public record Rate(int publicLimit, int authLimit, int loginLimit) {
    }

    public record Upload(long maxSize, String dir) {
    }

    public record Payment(String grpcAddr) {
    }

    /**
     * Reads the configuration.
     *
     * @throws IllegalStateException when a value is invalid or the settings contradict each other
     */
    public static AppConfig load(PropertyResolver resolver) {
        Settings s = new Settings(resolver);

        Duration accessExpiry = s.duration("JWT_ACCESS_EXPIRY", "15m");
        Duration refreshExpiry = s.duration("JWT_REFRESH_EXPIRY", "168h");
        Duration readTimeout = s.duration("APP_READ_TIMEOUT", "15s");
        // Must exceed APP_REQUEST_TIMEOUT so a timed-out request can still be sent its 504.
        Duration writeTimeout = s.duration("APP_WRITE_TIMEOUT", "35s");
        Duration idleTimeout = s.duration("APP_IDLE_TIMEOUT", "60s");
        Duration shutdownTimeout = s.duration("APP_SHUTDOWN_TIMEOUT", "30s");
        Duration requestTimeout = s.duration("APP_REQUEST_TIMEOUT", "30s");

        // The server stops writing once the write timeout passes, so a request timeout at or
        // beyond it means the client gets a dropped connection instead of the 504.
        if (writeTimeout.compareTo(requestTimeout) <= 0) {
            throw new IllegalStateException(String.format(
                    "APP_WRITE_TIMEOUT (%s) must be greater than APP_REQUEST_TIMEOUT (%s)",
                    GoDuration.format(writeTimeout), GoDuration.format(requestTimeout)));
        }

        String jwtSecret = s.string("JWT_SECRET", "");
        if (jwtSecret.isEmpty() || jwtSecret.equals(INSECURE_JWT_SECRET)) {
            throw new IllegalStateException("JWT_SECRET must be set to a strong, non-default value");
        }

        return new AppConfig(
                new App(
                        s.string("APP_PORT", "8080"),
                        s.string("APP_ENV", Env.DEVELOPMENT),
                        readTimeout,
                        writeTimeout,
                        idleTimeout,
                        shutdownTimeout,
                        requestTimeout),
                new Db(
                        s.string("DB_HOST", "localhost"),
                        s.string("DB_PORT", "5432"),
                        s.string("DB_USER", "postgres"),
                        s.string("DB_PASSWORD", ""),
                        s.string("DB_NAME", "mini_java_ecommerce"),
                        s.string("DB_SSLMODE", "disable")),
                new Redis(
                        s.string("REDIS_HOST", "localhost"),
                        s.string("REDIS_PORT", "6379"),
                        s.string("REDIS_PASSWORD", ""),
                        s.integer("REDIS_DB", 0)),
                new Nsq(
                        s.string("NSQ_LOOKUPD_ADDR", "localhost:4161"),
                        s.string("NSQD_ADDR", "localhost:4150")),
                new Jwt(jwtSecret, accessExpiry, refreshExpiry),
                new Rate(
                        s.integer("RATE_LIMIT_PUBLIC", 60),
                        s.integer("RATE_LIMIT_AUTH", 120),
                        s.integer("RATE_LIMIT_LOGIN", 10)),
                new Upload(
                        s.longValue("UPLOAD_MAX_SIZE", 5_242_880L),
                        s.string("UPLOAD_DIR", "./uploads")),
                new Payment(s.string("PAYMENT_GRPC_ADDR", "localhost:50051")));
    }

    /** Typed access to the settings; a blank value falls back to the default like an unset one. */
    private record Settings(PropertyResolver resolver) {

        String string(String key, String defaultValue) {
            String value = resolver.getProperty(key);
            return value == null || value.isBlank() ? defaultValue : value.trim();
        }

        /** Like viper's GetInt: a value that is not a number reads as 0. */
        int integer(String key, int defaultValue) {
            String value = string(key, null);
            if (value == null) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        long longValue(String key, long defaultValue) {
            String value = string(key, null);
            if (value == null) {
                return defaultValue;
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        Duration duration(String key, String defaultValue) {
            try {
                return GoDuration.parse(string(key, defaultValue));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("invalid " + key + ": " + e.getMessage(), e);
            }
        }
    }
}
