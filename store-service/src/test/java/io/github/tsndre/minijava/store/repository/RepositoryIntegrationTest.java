package io.github.tsndre.minijava.store.repository;

import com.zaxxer.hikari.HikariDataSource;
import io.github.tsndre.minijava.store.config.JacksonConfig;
import io.github.tsndre.minijava.store.constant.OrderStatus;
import io.github.tsndre.minijava.store.constant.PaymentMethod;
import io.github.tsndre.minijava.store.constant.PaymentStatus;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.model.Cart;
import io.github.tsndre.minijava.store.model.CartItem;
import io.github.tsndre.minijava.store.model.Category;
import io.github.tsndre.minijava.store.model.Order;
import io.github.tsndre.minijava.store.model.OrderItem;
import io.github.tsndre.minijava.store.model.Payment;
import io.github.tsndre.minijava.store.model.Product;
import io.github.tsndre.minijava.store.model.ProductFilter;
import io.github.tsndre.minijava.store.model.Review;
import io.github.tsndre.minijava.store.model.Store;
import io.github.tsndre.minijava.store.model.User;
import io.github.tsndre.minijava.store.repository.cache.Cache;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Runs the repositories against a real PostgreSQL when TEST_DATABASE_URL is set, for example:
 *
 * <pre>
 * TEST_DATABASE_URL="postgres://postgres:postgres@localhost:5432/mini_java_ecommerce?sslmode=disable" \
 *     ./mvnw -pl store-service -am test -Dtest=RepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * The migrations run into a fresh schema that is dropped afterwards, leaving the database's own
 * data alone. Without the variable the tests are skipped.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class RepositoryIntegrationTest {

    private static final String SCHEMA = "repository_test_" + System.nanoTime();

    private static HikariDataSource dataSource;
    private static JdbcClient jdbc;
    private static final MemCache cache = new MemCache();

    private static UserRepository users;
    private static StoreRepository stores;
    private static CategoryRepository categories;
    private static ProductRepository products;
    private static CartRepository carts;
    private static OrderRepository orders;
    private static ReviewRepository reviews;

    /** An in-memory cache that stores JSON, like the Redis cache. */
    static final class MemCache implements Cache {

        private final Map<String, String> data = new ConcurrentHashMap<>();
        private JsonMapper json;

        @Override
        public <T> Optional<T> get(String key, Class<T> type) {
            String value = data.get(key);
            return value == null ? Optional.empty() : Optional.of(json.readValue(value, type));
        }

        @Override
        public void set(String key, Object value, Duration ttl) {
            data.put(key, json.writeValueAsString(value));
        }

        @Override
        public void delete(String key) {
            data.remove(key);
        }

        @Override
        public boolean exists(String key) {
            return data.containsKey(key);
        }

        void clear() {
            data.clear();
        }
    }

    @BeforeAll
    static void migrateFreshSchema() {
        dataSource = new HikariDataSource();
        configure(dataSource, System.getenv("TEST_DATABASE_URL"));
        JdbcClient.create(dataSource).sql("CREATE SCHEMA " + SCHEMA).update();
        dataSource.close();

        dataSource = new HikariDataSource();
        configure(dataSource, System.getenv("TEST_DATABASE_URL"));
        dataSource.addDataSourceProperty("currentSchema", SCHEMA);
        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration").load().migrate();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(JacksonConfig.class)
                .run(context -> cache.json = context.getBean(JsonMapper.class));

        jdbc = JdbcClient.create(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        users = new JdbcUserRepository(jdbc);
        stores = new JdbcStoreRepository(jdbc);
        categories = new JdbcCategoryRepository(jdbc);
        products = new JdbcProductRepository(jdbc, cache);
        carts = new JdbcCartRepository(jdbc, transactions, cache);
        orders = new JdbcOrderRepository(jdbc, transactions, cache);
        reviews = new JdbcReviewRepository(jdbc);
    }

    @AfterAll
    static void dropSchema() {
        if (dataSource != null) {
            jdbc.sql("DROP SCHEMA " + SCHEMA + " CASCADE").update();
            dataSource.close();
        }
    }

    /** Accepts a JDBC URL or a postgres:// URL like the Go tests use. */
    private static void configure(HikariDataSource ds, String url) {
        if (url.startsWith("jdbc:")) {
            ds.setJdbcUrl(url);
            return;
        }
        URI uri = URI.create(url);
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        ds.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + ":" + (uri.getPort() < 0 ? 5432 : uri.getPort())
                + uri.getRawPath() + query);
        if (uri.getUserInfo() != null) {
            String[] credentials = uri.getUserInfo().split(":", 2);
            ds.setUsername(credentials[0]);
            ds.setPassword(credentials.length > 1 ? credentials[1] : "");
        }
    }

    // Every row the fixtures create gets unique values, so the tests can share one schema.

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static User user() {
        return users.create(User.builder().email(unique("user") + "@example.com").password("hash")
                .name(unique("User")).role(Role.BUYER).build());
    }

    private static Store store() {
        return stores.create(Store.builder().userId(user().getId()).name(unique("Store")).build());
    }

    private static Category category() {
        return categories.create(Category.builder().name(unique("Category")).build());
    }

    private static Product product(UUID storeId, UUID categoryId, String name, String price, int stock) {
        return products.create(Product.builder().storeId(storeId).categoryId(categoryId).name(name)
                .price(new BigDecimal(price)).stock(stock).build());
    }

    /** A product's stock read from the database, bypassing the cache. */
    private static int stock(UUID productId) {
        return jdbc.sql("SELECT stock FROM products WHERE id = ?").param(productId).query(Integer.class).single();
    }

    /** Checks out quantity of product for buyer as one pending order with a pending payment. */
    private static Order placeOrder(User buyer, Product product, int quantity) {
        return orders.createOrdersWithStock(List.of(new StockReservation(product.getId(), quantity)), reserved -> {
            BigDecimal total = reserved.getFirst().getPrice().multiply(BigDecimal.valueOf(quantity));
            return List.of(Order.builder()
                    .userId(buyer.getId())
                    .storeId(reserved.getFirst().getStoreId())
                    .status(OrderStatus.PENDING)
                    .totalAmount(total)
                    .shippingAddress("Jl. Test 1")
                    .orderItems(new ArrayList<>(List.of(OrderItem.builder().productId(reserved.getFirst().getId())
                            .quantity(quantity).price(reserved.getFirst().getPrice()).build())))
                    .payment(Payment.builder().method(PaymentMethod.MOCK).status(PaymentStatus.PENDING)
                            .amount(total).build())
                    .build());
        }).getFirst();
    }

    private static List<UUID> ids(List<?> rows) {
        return rows.stream().map(row -> row instanceof Order o ? o.getId() : ((Product) row).getId()).toList();
    }

    @Test
    void users() {
        User user = user();
        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();

        assertThat(users.findByEmail(user.getEmail().toUpperCase())).hasValueSatisfying(
                found -> assertThat(found.getId()).isEqualTo(user.getId()));

        assertThatThrownBy(() -> users.create(User.builder().email(user.getEmail()).password("hash").name("Duplicate")
                .role(Role.BUYER).build())).isInstanceOf(UniqueViolationException.class);

        users.updateRole(user.getId(), Role.SELLER);
        assertThat(users.findById(user.getId()).orElseThrow().getRole()).isEqualTo(Role.SELLER);

        assertThat(users.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void stores() {
        Store store = store();

        assertThatThrownBy(() -> stores.create(Store.builder().userId(store.getUserId()).name("Second store").build()))
                .isInstanceOf(UniqueViolationException.class);

        store.setName("Renamed");
        store.setDescription("New description");
        store.setLogoUrl("/uploads/stores/logo.png");
        stores.update(store);
        Store found = stores.findByUserId(store.getUserId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("Renamed");
        assertThat(found.getDescription()).isEqualTo("New description");
        assertThat(found.getLogoUrl()).isEqualTo("/uploads/stores/logo.png");

        assertThatThrownBy(() -> stores.update(Store.builder().id(UUID.randomUUID()).name("Missing").build()))
                .isInstanceOf(RecordNotFoundException.class);

        stores.delete(store.getId());
        assertThat(stores.findById(store.getId())).isEmpty();
    }

    @Test
    void categories() {
        Category first = categories.create(Category.builder().name(unique("A category")).build());
        Category second = categories.create(Category.builder().name(unique("B category")).build());

        assertThatThrownBy(() -> categories.create(Category.builder().name(first.getName()).build()))
                .isInstanceOf(UniqueViolationException.class);
        assertThatThrownBy(() -> categories.update(Category.builder().id(second.getId()).name(first.getName()).build()))
                .isInstanceOf(UniqueViolationException.class);

        List<UUID> sorted = categories.findAll().stream().map(Category::getId).toList();
        assertThat(sorted.indexOf(first.getId())).as("categories are sorted by name")
                .isLessThan(sorted.indexOf(second.getId()));

        product(store().getId(), first.getId(), unique("Product"), "10.00", 1);
        assertThatThrownBy(() -> categories.delete(first.getId())).isInstanceOf(ForeignKeyViolationException.class);

        categories.delete(second.getId());
        assertThat(categories.findById(second.getId())).isEmpty();
    }

    @Nested
    class Products {

        private final Store store = store();
        private final Category category = category();

        @Test
        void createRequiresAnExistingCategory() {
            assertThatThrownBy(() -> products.create(Product.builder().storeId(store.getId()).categoryId(UUID.randomUUID())
                    .name("Orphan").price(BigDecimal.ONE).build())).isInstanceOf(ForeignKeyViolationException.class);
        }

        @Test
        void updateNeverWritesStock() {
            Product product = product(store.getId(), category.getId(), unique("Mug"), "100.50", 5);
            products.updateStock(product.getId(), 7);

            product.setName("Renamed mug"); // still holds stock 5
            product.setPrice(new BigDecimal("99.99"));
            Product stored = products.update(product);
            assertThat(stored.getStock()).as("update reads back the stored stock").isEqualTo(7);
            assertThat(stock(product.getId())).isEqualTo(7);

            Product found = products.findById(product.getId()).orElseThrow();
            assertThat(found.getName()).isEqualTo("Renamed mug");
            assertThat(found.getPrice()).isEqualByComparingTo("99.99");
        }

        @Test
        void findAllFiltersSortsAndPages() {
            Store shop = store();
            Category other = category();
            String token = unique("findall");
            Product cheap = product(shop.getId(), category.getId(), token + " cheap", "5.00", 1);
            Product mid = product(shop.getId(), category.getId(), token + " mid", "50.00", 1);
            Product pricey = product(shop.getId(), other.getId(), token + " pricey", "500.00", 1);

            PageResult<Product> page = products.findAll(new ProductFilter("", shop.getId().toString(),
                    token.toUpperCase(), "10", "", "price", "asc", 1, 10));
            assertThat(page.total()).isEqualTo(2);
            assertThat(ids(page.items())).containsExactly(mid.getId(), pricey.getId());

            page = products.findAll(new ProductFilter(other.getId().toString(), shop.getId().toString(), "", "", "",
                    "", "", 1, 10));
            assertThat(page.total()).isEqualTo(1);
            assertThat(ids(page.items())).containsExactly(pricey.getId());

            page = products.findAll(new ProductFilter("", shop.getId().toString(), "", "", "", "price", "desc", 2, 2));
            assertThat(page.total()).as("the total ignores pagination").isEqualTo(3);
            assertThat(ids(page.items())).containsExactly(cheap.getId());
        }

        @Test
        void deleteRefusesProductsThatOrdersReference() {
            Product ordered = product(store.getId(), category.getId(), unique("Lamp"), "20.00", 3);
            placeOrder(user(), ordered, 1);
            assertThatThrownBy(() -> products.delete(ordered.getId())).isInstanceOf(ForeignKeyViolationException.class);

            Product unused = product(store.getId(), category.getId(), unique("Vase"), "20.00", 3);
            products.delete(unused.getId());
            assertThat(products.findById(unused.getId())).isEmpty();
        }
    }

    @Test
    void carts() {
        User buyer = user();
        Store store = store();
        Category category = category();
        Product first = product(store.getId(), category.getId(), unique("Pen"), "10.00", 5);
        Product second = product(store.getId(), category.getId(), unique("Book"), "20.00", 5);

        Cart cart = new Cart(buyer.getId());
        cart.getItems().add(CartItem.builder().productId(first.getId()).quantity(2).build());
        cart.getItems().add(CartItem.builder().productId(second.getId()).quantity(1).build());
        carts.saveCart(cart);

        // Without the cached copy, getCart reads the PostgreSQL backup.
        cache.clear();
        Cart loaded = carts.getCart(buyer.getId());
        assertThat(loaded.getItems()).hasSize(2);
        CartItem firstItem = loaded.getItems().stream().filter(i -> i.getProductId().equals(first.getId())).findFirst()
                .orElseThrow();
        assertThat(firstItem.getQuantity()).isEqualTo(2);
        assertThat(firstItem.getName()).isEqualTo(first.getName());
        assertThat(firstItem.getPrice()).isEqualByComparingTo(first.getPrice());
        assertThat(loaded.getUpdatedAt()).isEqualTo(Cart.ZERO_TIME);

        cart.getItems().removeLast();
        carts.saveCart(cart);
        cache.clear();
        assertThat(carts.getCart(buyer.getId()).getItems()).as("saving replaces the stored items").hasSize(1);

        carts.deleteCart(buyer.getId());
        assertThat(carts.getCart(buyer.getId()).getItems()).as("an empty cart has an empty list").isNotNull().isEmpty();
    }

    @Nested
    class Orders {

        private final User buyer = user();
        private final Store store = store();
        private final Category category = category();

        @Test
        void checkoutReservesStockAndStoresTheOrder() {
            Product product = product(store.getId(), category.getId(), unique("Mug"), "25.00", 5);
            Order order = placeOrder(buyer, product, 2);
            assertThat(order.getId()).isNotNull();
            assertThat(order.getOrderItems()).singleElement().satisfies(i -> assertThat(i.getId()).isNotNull());
            assertThat(order.getPayment().getId()).isNotNull();
            assertThat(stock(product.getId())).isEqualTo(3);

            Order found = orders.findById(order.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(found.getTotalAmount()).isEqualByComparingTo("50.00");
            assertThat(found.getOrderItems()).singleElement().satisfies(i -> assertThat(i.getQuantity()).isEqualTo(2));
            assertThat(found.getPayment().getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(found.getPayment().getPaidAt()).isNull();
        }

        @Test
        void aFailedReservationWritesNothing() {
            Product scarce = product(store.getId(), category.getId(), unique("Scarce"), "10.00", 1);
            Product plenty = product(store.getId(), category.getId(), unique("Plenty"), "10.00", 5);

            assertThatThrownBy(() -> orders.createOrdersWithStock(List.of(
                    new StockReservation(plenty.getId(), 1),
                    new StockReservation(scarce.getId(), 2)), reserved -> fail("build must not run")))
                    .isInstanceOfSatisfying(StockUnavailableException.class,
                            e -> assertThat(e.getProductName()).isEqualTo(scarce.getName()));
            assertThat(stock(plenty.getId())).as("the earlier reservation is rolled back").isEqualTo(5);
            assertThat(stock(scarce.getId())).isEqualTo(1);

            assertThatThrownBy(() -> orders.createOrdersWithStock(List.of(new StockReservation(UUID.randomUUID(), 1)),
                    reserved -> fail("build must not run"))).isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        void concurrentCheckoutsCannotOversell() throws Exception {
            Product product = product(store.getId(), category.getId(), unique("Last one"), "10.00", 1);
            List<User> buyers = List.of(user(), user(), user());

            List<Future<Order>> results = new ArrayList<>();
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (User b : buyers) {
                    results.add(executor.submit(() -> placeOrder(b, product, 1)));
                }
            }

            int succeeded = 0;
            for (Future<Order> result : results) {
                try {
                    result.get();
                    succeeded++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(StockUnavailableException.class);
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(stock(product.getId())).isZero();
        }

        @Test
        void listsByBuyerAndByStoreNewestFirstWithDetails() {
            User shopper = user();
            Store shop = store();
            Product product = product(shop.getId(), category.getId(), unique("Lamp"), "15.00", 10);
            Order older = placeOrder(shopper, product, 1);
            Order newer = placeOrder(shopper, product, 2);

            PageResult<Order> page = orders.findByUserId(shopper.getId(), 1, 10);
            assertThat(page.total()).isEqualTo(2);
            assertThat(ids(page.items())).containsExactly(newer.getId(), older.getId());
            page.items().forEach(o -> {
                assertThat(o.getOrderItems()).hasSize(1);
                assertThat(o.getPayment()).isNotNull();
            });

            PageResult<Order> second = orders.findByStoreId(shop.getId(), 2, 1);
            assertThat(second.total()).isEqualTo(2);
            assertThat(ids(second.items())).containsExactly(older.getId());
        }

        @Test
        void statusUpdatesAreCompareAndSet() {
            Product product = product(store.getId(), category.getId(), unique("Pencil"), "3.00", 10);
            Order order = placeOrder(buyer, product, 1);

            assertThat(orders.updateStatusIfCurrent(order.getId(), OrderStatus.PAID, OrderStatus.PROCESSING))
                    .as("the order is still pending").isFalse();

            assertThat(orders.markPaymentSucceeded(order.getId())).isTrue();
            assertThat(orders.markPaymentSucceeded(order.getId())).as("a duplicate payment result changes nothing")
                    .isFalse();

            Order found = orders.findById(order.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(found.getPayment().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(found.getPayment().getPaidAt()).isNotNull();

            assertThat(orders.updateStatusIfCurrent(order.getId(), OrderStatus.PAID, OrderStatus.PROCESSING)).isTrue();
        }

        @Test
        void cancellationAndFailedPaymentsRestockOnce() {
            Product product = product(store.getId(), category.getId(), unique("Plate"), "8.00", 10);
            Order cancelled = placeOrder(buyer, product, 3);
            Order failed = placeOrder(buyer, product, 2);
            assertThat(stock(product.getId())).isEqualTo(5);

            assertThat(orders.cancelAndRestock(cancelled.getId(), OrderStatus.PENDING)).isTrue();
            assertThat(orders.cancelAndRestock(cancelled.getId(), OrderStatus.PENDING))
                    .as("cancelling again changes nothing").isFalse();
            assertThat(stock(product.getId())).isEqualTo(8);

            assertThat(orders.markPaymentFailed(failed.getId())).isTrue();
            assertThat(stock(product.getId())).isEqualTo(10);

            Map.of(cancelled.getId(), PaymentStatus.CANCELLED, failed.getId(), PaymentStatus.FAILED)
                    .forEach((id, paymentStatus) -> {
                        Order found = orders.findById(id).orElseThrow();
                        assertThat(found.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                        assertThat(found.getPayment().getStatus()).isEqualTo(paymentStatus);
                    });
        }

        @Test
        void stalePendingOrders() {
            Product product = product(store.getId(), category.getId(), unique("Cup"), "1.00", 10);
            Order order = placeOrder(buyer, product, 1);

            assertThat(ids(orders.findStalePending(OffsetDateTime.now().plusMinutes(1), 1000))).contains(order.getId());
            assertThat(ids(orders.findStalePending(OffsetDateTime.now().minusHours(1), 1000))).doesNotContain(order.getId());
        }
    }

    @Test
    void reviews() {
        User buyer = user();
        Product product = product(store().getId(), category().getId(), unique("Chair"), "12.00", 5);

        assertThat(reviews.hasUserPurchased(buyer.getId(), product.getId())).isFalse();

        Order order = placeOrder(buyer, product, 1);
        assertThat(reviews.hasUserPurchased(buyer.getId(), product.getId()))
                .as("a pending order does not count as a purchase").isFalse();

        assertThat(orders.updateStatusIfCurrent(order.getId(), OrderStatus.PENDING, OrderStatus.SHIPPED)).isTrue();
        assertThat(reviews.hasUserPurchased(buyer.getId(), product.getId())).isTrue();

        Review review = reviews.create(Review.builder().userId(buyer.getId()).productId(product.getId()).rating(4)
                .comment("Solid").build());
        assertThat(review.getId()).isNotNull();

        assertThatThrownBy(() -> reviews.create(Review.builder().userId(buyer.getId()).productId(product.getId())
                .rating(1).build())).isInstanceOf(UniqueViolationException.class);

        assertThat(reviews.hasUserReviewed(buyer.getId(), product.getId())).isTrue();

        PageResult<Review> page = reviews.findByProductId(product.getId(), 1, 10);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(r -> {
            assertThat(r.getUserName()).isEqualTo(buyer.getName());
            assertThat(r.getComment()).isEqualTo("Solid");
        });
    }
}
