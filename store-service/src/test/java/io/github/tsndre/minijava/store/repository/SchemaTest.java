package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.constant.OrderStatus;
import io.github.tsndre.minijava.store.constant.PaymentMethod;
import io.github.tsndre.minijava.store.constant.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The SQL in the repositories is written by hand. These tests check the column lists and the row
 * mappers against the schema in the Flyway migrations, so a misspelled column fails here instead of
 * at runtime, without needing a database.
 */
class SchemaTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Pattern CREATE_TABLE = Pattern.compile("(?s)CREATE TABLE (\\w+) \\((.*?)\\n\\);");
    private static final Pattern ADD_COLUMN = Pattern.compile("ALTER TABLE (\\w+) ADD COLUMN (\\w+)");
    private static final Set<String> TABLE_CONSTRAINTS = Set.of("primary", "foreign", "check", "constraint");

    private static Map<String, Set<String>> schema;

    @BeforeAll
    static void readMigrations() throws IOException {
        schema = new HashMap<>();
        List<Path> files;
        try (Stream<Path> paths = Files.list(MIGRATIONS)) {
            files = paths.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
        }
        assertThat(files).isNotEmpty();

        for (Path file : files) {
            String sql = Files.readString(file);
            Matcher table = CREATE_TABLE.matcher(sql);
            while (table.find()) {
                Set<String> columns = new HashSet<>();
                for (String line : table.group(2).split("\n")) {
                    String[] fields = line.strip().split("\\s+");
                    String name = fields[0].toLowerCase();
                    // Table constraints, e.g. UNIQUE(user_id, product_id), are not columns.
                    if (name.isEmpty() || name.startsWith("unique") || TABLE_CONSTRAINTS.contains(name)) {
                        continue;
                    }
                    columns.add(name);
                }
                schema.put(table.group(1), columns);
            }
            Matcher column = ADD_COLUMN.matcher(sql);
            while (column.find()) {
                assertThat(schema).as("%s alters a table no migration creates", file).containsKey(column.group(1));
                schema.get(column.group(1)).add(column.group(2));
            }
        }
    }

    @Test
    void columnListsMatchSchema() {
        Map<String, String> lists = Map.of(
                "users", JdbcUserRepository.COLUMNS,
                "stores", JdbcStoreRepository.COLUMNS,
                "categories", JdbcCategoryRepository.COLUMNS,
                "products", JdbcProductRepository.COLUMNS,
                "reviews", JdbcReviewRepository.COLUMNS,
                "orders", JdbcOrderRepository.ORDER_COLUMNS,
                "order_items", JdbcOrderRepository.ORDER_ITEM_COLUMNS,
                "payments", JdbcOrderRepository.PAYMENT_COLUMNS);

        lists.forEach((table, list) -> {
            assertThat(schema).as("table %s is not in the migrations", table).containsKey(table);
            for (String column : list.split(", ")) {
                assertThat(schema.get(table)).as("column %s.%s does not exist", table, column).contains(column);
            }
        });
    }

    @Test
    void rowMappersReadOnlyMigratedColumns() {
        assertReadsOnly(RowMappers.USER, "users");
        assertReadsOnly(RowMappers.STORE, "stores");
        assertReadsOnly(RowMappers.CATEGORY, "categories");
        assertReadsOnly(RowMappers.PRODUCT, "products");
        assertReadsOnly(RowMappers.REVIEW, "reviews");
        assertReadsOnly(RowMappers.ORDER, "orders");
        assertReadsOnly(RowMappers.ORDER_ITEM, "order_items");
        assertReadsOnly(RowMappers.PAYMENT, "payments");
        // The cart is read by joining cart_items with products.
        assertReadsOnly(RowMappers.CART_ITEM, "cart_items", "products");
    }

    private static void assertReadsOnly(RowMapper<?> mapper, String... tables) {
        Set<String> columns = new HashSet<>();
        Arrays.stream(tables).forEach(table -> columns.addAll(schema.get(table)));
        assertThatCode(() -> mapper.mapRow(resultSetWith(columns), 0))
                .as("mapper for %s", String.join(" + ", tables))
                .doesNotThrowAnyException();
    }

    /** A row that has only the given columns; reading any other column fails like a real driver. */
    private static ResultSet resultSetWith(Set<String> columns) {
        return (ResultSet) Proxy.newProxyInstance(SchemaTest.class.getClassLoader(), new Class<?>[] {ResultSet.class},
                (proxy, method, args) -> {
                    if (args == null || !(args[0] instanceof String column)) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    if (!columns.contains(column)) {
                        throw new SQLException("column \"" + column + "\" does not exist");
                    }
                    return switch (method.getName()) {
                        case "getString" -> switch (column) {
                            // Enum columns must hold a value their enum knows; "pending" is both an
                            // order and a payment status.
                            case "role" -> Role.BUYER.value();
                            case "status" -> OrderStatus.PENDING.value();
                            case "method" -> PaymentMethod.MOCK.value();
                            default -> "text";
                        };
                        case "getInt" -> 1;
                        case "getBigDecimal" -> BigDecimal.ONE;
                        case "getObject" -> args[1] == UUID.class ? UUID.randomUUID() : OffsetDateTime.now();
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }
}
