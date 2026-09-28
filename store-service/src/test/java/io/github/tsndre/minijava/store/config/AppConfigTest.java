package io.github.tsndre.minijava.store.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppConfigTest {

    private static MockEnvironment environment() {
        return new MockEnvironment().withProperty("JWT_SECRET", "a-strong-test-secret");
    }

    @Test
    void defaultsMatchTheGoService() {
        AppConfig config = AppConfig.load(environment());

        assertThat(config.app().port()).isEqualTo("8080");
        assertThat(config.app().env()).isEqualTo("development");
        assertThat(config.db().name()).isEqualTo("mini_java_ecommerce");
        assertThat(config.db().jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/mini_java_ecommerce?sslmode=disable");
        assertThat(config.redis().addr()).isEqualTo("localhost:6379");
        assertThat(config.jwt().accessExpiry()).isEqualTo(Duration.ofMinutes(15));
        assertThat(config.jwt().refreshExpiry()).isEqualTo(Duration.ofHours(168));
        assertThat(config.rate().loginLimit()).isEqualTo(10);
        assertThat(config.upload().maxSize()).isEqualTo(5_242_880L);
        assertThat(config.payment().grpcAddr()).isEqualTo("localhost:50051");
    }

    @Test
    void defaultTimeoutsAreConsistent() {
        // Empty values fall back to the defaults, as viper ignores empty env vars.
        AppConfig config = AppConfig.load(environment()
                .withProperty("APP_WRITE_TIMEOUT", "")
                .withProperty("APP_REQUEST_TIMEOUT", ""));

        assertThat(config.app().requestTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.app().writeTimeout()).isGreaterThan(config.app().requestTimeout());
    }

    @ParameterizedTest
    @CsvSource({
            "15s, 30s, true",
            "30s, 30s, true",
            "35s, 30s, false",
    })
    void rejectsWriteTimeoutNotAboveRequestTimeout(String write, String request, boolean wantError) {
        MockEnvironment env = environment()
                .withProperty("APP_WRITE_TIMEOUT", write)
                .withProperty("APP_REQUEST_TIMEOUT", request);

        if (wantError) {
            assertThatThrownBy(() -> AppConfig.load(env))
                    .hasMessage("APP_WRITE_TIMEOUT (" + write + ") must be greater than APP_REQUEST_TIMEOUT (" + request + ")");
        } else {
            assertThatCode(() -> AppConfig.load(env)).doesNotThrowAnyException();
        }
    }

    @Test
    void invalidDurationNamesTheVariable() {
        assertThatThrownBy(() -> AppConfig.load(environment().withProperty("JWT_ACCESS_EXPIRY", "15 minutes")))
                .hasMessage("invalid JWT_ACCESS_EXPIRY: time: unknown unit \" minutes\" in duration \"15 minutes\"");
    }

    @Test
    void requiresAStrongJwtSecret() {
        assertThatThrownBy(() -> AppConfig.load(new MockEnvironment()))
                .hasMessage("JWT_SECRET must be set to a strong, non-default value");
        assertThatThrownBy(() -> AppConfig.load(new MockEnvironment()
                .withProperty("JWT_SECRET", "your-super-secret-key-change-this")))
                .hasMessage("JWT_SECRET must be set to a strong, non-default value");
    }

    @Test
    void numbersThatDoNotParseReadAsZeroLikeViper() {
        assertThat(AppConfig.load(environment().withProperty("RATE_LIMIT_PUBLIC", "many")).rate().publicLimit()).isZero();
    }
}
