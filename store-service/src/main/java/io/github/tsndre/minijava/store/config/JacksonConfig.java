package io.github.tsndre.minijava.store.config;

import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * JSON as the Go service reads and writes it: snake_case names in declaration order, decimals as
 * strings, timestamps with their original offset, HTML-safe escaping, a newline after each
 * response, and strict types when reading (a number is never accepted for a string and vice versa).
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    JsonFactoryBuilderCustomizer goJsonFactory() {
        return factory -> factory.characterEscapes(new GoJson.HtmlSafeEscapes());
    }

    @Bean
    JsonMapperBuilderCustomizer goJsonMapper() {
        return builder -> builder
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(JsonWriteFeature.WRITE_HEX_UPPER_CASE)
                .disable(JsonWriteFeature.ESCAPE_FORWARD_SLASHES)
                .withCoercionConfig(LogicalType.Textual, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Integer, cfg -> cfg
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .addModule(GoJson.decimalModule());
    }

    /** Ends every JSON response with a newline, as Go's json.Encoder does. */
    @Bean
    JacksonJsonHttpMessageConverter jacksonJsonHttpMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonHttpMessageConverter(jsonMapper) {
            @Override
            protected void writeSuffix(JsonGenerator generator, Object object) {
                generator.writeRaw('\n');
            }
        };
    }
}
