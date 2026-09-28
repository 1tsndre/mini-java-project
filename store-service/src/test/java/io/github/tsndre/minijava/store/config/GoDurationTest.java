package io.github.tsndre.minijava.store.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoDurationTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "15m, 900000000000",
            "168h, 604800000000000",
            "1h30m, 5400000000000",
            "1.5h, 5400000000000",
            "300ms, 300000000",
            "-2s, -2000000000",
            "+5s, 5000000000",
            "1us, 1000",
            "1µs, 1000",
            "7ns, 7",
            ".5s, 500000000",
    })
    void parse(String value, long nanos) {
        assertThat(GoDuration.parse(value)).isEqualTo(Duration.ofNanos(nanos));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "''|time: invalid duration \"\"",
            "abc|time: invalid duration \"abc\"",
            "10|time: missing unit in duration \"10\"",
            "5x|time: unknown unit \"x\" in duration \"5x\"",
            "1..5s|time: missing unit in duration \"1..5s\"",
            ".s|time: invalid duration \".s\"",
            "٣s|time: invalid duration \"\\xd9\\xa3s\"",
            "5µx|time: unknown unit \"\\xc2\\xb5x\" in duration \"5\\xc2\\xb5x\"",
            "9223372036854775808ns|time: invalid duration \"9223372036854775808ns\"",
    })
    void parseErrorsReadLikeGo(String value, String message) {
        assertThatThrownBy(() -> GoDuration.parse(value)).hasMessage(message);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0s",
            "35000000000, 35s",
            "60000000000, 1m0s",
            "5400000000000, 1h30m0s",
            "250000000, 250ms",
            "1500, 1.5µs",
            "42, 42ns",
            "1234567890, 1.23456789s",
            "-2000000000, -2s",
    })
    void formatsLikeGo(long nanos, String expected) {
        assertThat(GoDuration.format(Duration.ofNanos(nanos))).isEqualTo(expected);
    }
}
