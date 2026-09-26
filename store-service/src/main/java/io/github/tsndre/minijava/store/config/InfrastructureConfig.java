package io.github.tsndre.minijava.store.config;

import com.zaxxer.hikari.HikariDataSource;
import io.github.tsndre.minijava.common.constant.Env;
import io.github.tsndre.minijava.common.jwt.JwtManager;
import io.github.tsndre.minijava.common.upload.Uploader;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.DefaultLifecycleProcessor;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import javax.sql.DataSource;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class InfrastructureConfig {

    @Bean
    AppConfig appConfig(Environment environment) {
        return AppConfig.load(environment);
    }

    @Bean
    DataSource dataSource(AppConfig config) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(config.db().jdbcUrl());
        dataSource.setUsername(config.db().user());
        dataSource.setPassword(config.db().password());
        // Statements are logged in development, like the Go service's query tracer.
        if (Env.DEVELOPMENT.equals(config.app().env())) {
            return SqlLoggingDataSource.wrap(dataSource);
        }
        return dataSource;
    }

    /** Starts the development SQL log once the startup migrations have run. */
    @EventListener(ApplicationReadyEvent.class)
    void startSqlLogging(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getBean(DataSource.class) instanceof SqlLoggingDataSource dataSource) {
            dataSource.enable();
        }
    }

    @Bean
    LettuceConnectionFactory redisConnectionFactory(AppConfig config) {
        AppConfig.Redis redis = config.redis();
        RedisStandaloneConfiguration standalone =
                new RedisStandaloneConfiguration(redis.host(), Integer.parseInt(redis.port()));
        standalone.setPassword(RedisPassword.of(redis.password()));
        standalone.setDatabase(redis.db());
        return new LettuceConnectionFactory(standalone);
    }

    /** Like the Go service, refuse to start without Redis. */
    @Bean
    SmartInitializingSingleton redisStartupCheck(RedisConnectionFactory connectionFactory) {
        return () -> {
            try (RedisConnection connection = connectionFactory.getConnection()) {
                connection.ping();
            } catch (RuntimeException e) {
                throw new IllegalStateException("failed to connect to redis", e);
            }
            log.info("connected to redis");
        };
    }

    @Bean
    JwtManager jwtManager(AppConfig config) {
        AppConfig.Jwt jwt = config.jwt();
        return new JwtManager(jwt.secret(), jwt.accessExpiry(), jwt.refreshExpiry());
    }

    @Bean
    Uploader uploader(AppConfig config) {
        return new Uploader(config.upload().dir(), config.upload().maxSize());
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> serverSettings(AppConfig config) {
        AppConfig.App app = config.app();
        return factory -> {
            factory.setPort(Integer.parseInt(app.port()));
            factory.addConnectorCustomizers(connector -> {
                if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol<?> http) {
                    http.setConnectionTimeout((int) app.readTimeout().toMillis());
                    http.setKeepAliveTimeout((int) app.idleTimeout().toMillis());
                }
            });
        };
    }

    /** In-flight requests get APP_SHUTDOWN_TIMEOUT to finish when the service stops. */
    @Bean(name = AbstractApplicationContext.LIFECYCLE_PROCESSOR_BEAN_NAME)
    DefaultLifecycleProcessor lifecycleProcessor(AppConfig config) {
        DefaultLifecycleProcessor processor = new DefaultLifecycleProcessor();
        processor.setTimeoutPerShutdownPhase(config.app().shutdownTimeout().toMillis());
        return processor;
    }
}
