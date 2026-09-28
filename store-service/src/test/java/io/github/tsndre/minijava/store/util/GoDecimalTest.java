package io.github.tsndre.minijava.store.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** shopspring/decimal's NewFromString and String, as the Go service uses them. */
class GoDecimalTest {

    @ParameterizedTest
    @CsvSource({
            "15000, 15000",
            "15000.50, 15000.50",
            "1e3, 1000",
            "1E-2, 0.01",
            ".5, 0.5",
            "-.5, -0.5",
            ".-5, -0.05",
            "+7, 7",
            "1., 1",
            "12345678901234567890.5, 12345678901234567890.5",
    })
    void parses(String text, String expected) {
        assertThat(GoDecimal.parse(text)).hasValueSatisfying(d -> assertThat(d).isEqualByComparingTo(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ".", "-", "abc", "1.2.3", "1e", "1e1.5", " 1", "1_000", "0x10", "NaN", "١٢"})
    void rejects(String text) {
        assertThat(GoDecimal.parse(text)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "15000.00, 15000",
            "15000.50, 15000.5",
            "0.00, 0",
            "-1.10, -1.1",
            "1E+3, 1000",
            "0.010, 0.01",
    })
    void formatsWithoutTrailingZeros(String value, String expected) {
        assertThat(GoDecimal.format(new BigDecimal(value))).isEqualTo(expected);
    }
}
