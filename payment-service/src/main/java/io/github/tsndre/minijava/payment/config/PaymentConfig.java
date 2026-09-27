package io.github.tsndre.minijava.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class PaymentConfig {

    @Bean
    AppConfig appConfig(Environment environment) {
        return AppConfig.load(environment);
    }
}
