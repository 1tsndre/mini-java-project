package io.github.tsndre.minijava.store.pagination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PagesTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0, 1, 10",
            "-5, -1, 1, 10",
            "3, 25, 3, 25",
            "2, 101, 2, 100",
            "9223372036854775807, 99999999999, 9223372036854775807, 100",
    })
    void normalize(long page, long perPage, long wantPage, int wantPerPage) {
        assertThat(Pages.normalize(page, perPage)).isEqualTo(new Pages.Page(wantPage, wantPerPage));
    }

    @Test
    void totalPages() {
        assertThat(Pages.totalPages(0, 10)).isZero();
        assertThat(Pages.totalPages(10, 10)).isEqualTo(1);
        assertThat(Pages.totalPages(11, 10)).isEqualTo(2);
    }

    /** Like Go's strconv.Atoi with the error ignored. */
    @ParameterizedTest
    @CsvSource({
            "'', 0",
            "7, 7",
            "+7, 7",
            "-7, -7",
            "007, 7",
            "abc, 0",
            "1.5, 0",
            "1_000, 0",
            "+, 0",
            "٣, 0",
            "99999999999999999999, 9223372036854775807",
            "-99999999999999999999, -9223372036854775808",
            "9223372036854775807, 9223372036854775807",
            "-9223372036854775808, -9223372036854775808",
    })
    void parse(String value, long expected) {
        assertThat(Pages.parse(value)).isEqualTo(expected);
    }
}
