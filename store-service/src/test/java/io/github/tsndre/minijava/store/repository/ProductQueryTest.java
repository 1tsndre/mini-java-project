package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.model.ProductFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductQueryTest {

    private static ProductFilter filter(String categoryId, String storeId, String search, String minPrice,
                                        String maxPrice, String sortBy, String sortOrder) {
        return new ProductFilter(categoryId, storeId, search, minPrice, maxPrice, sortBy, sortOrder, 1, 10);
    }

    @Test
    void noFilters() {
        JdbcProductRepository.Where where = JdbcProductRepository.filterQuery(filter("", "", "", "", "", "", ""));

        assertThat(where.sql()).isEmpty();
        assertThat(where.args()).isEmpty();
    }

    @Test
    void allFiltersWithTheSearchArgumentUsedTwice() {
        JdbcProductRepository.Where where =
                JdbcProductRepository.filterQuery(filter("cat", "store", "mug", "10", "20.5", "", ""));

        assertThat(where.sql()).isEqualTo(" WHERE category_id = CAST(? AS uuid) AND store_id = CAST(? AS uuid)"
                + " AND (name ILIKE ? OR description ILIKE ?) AND price >= ? AND price <= ?");
        assertThat(where.args()).containsExactly("cat", "store", "%mug%", "%mug%", new BigDecimal("10"),
                new BigDecimal("20.5"));
    }

    @Test
    void unparsablePricesAreIgnored() {
        JdbcProductRepository.Where where = JdbcProductRepository.filterQuery(filter("", "", "", "cheap", "20", "", ""));

        assertThat(where.sql()).isEqualTo(" WHERE price <= ?");
        assertThat(where.args()).isEqualTo(List.of(new BigDecimal("20")));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "'', '', ' ORDER BY created_at DESC, id DESC'",
            "price, asc, ' ORDER BY price ASC, id ASC'",
            "name, desc, ' ORDER BY name DESC, id DESC'",
            // Anything outside the allow-list falls back to the default column.
            "'price; DROP TABLE products', asc, ' ORDER BY created_at ASC, id ASC'",
    })
    void orderBy(String sortBy, String sortOrder, String expected) {
        assertThat(JdbcProductRepository.orderBy(filter("", "", "", "", "", sortBy, sortOrder))).isEqualTo(expected);
    }
}
