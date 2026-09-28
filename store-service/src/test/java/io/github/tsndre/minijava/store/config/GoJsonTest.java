package io.github.tsndre.minijava.store.config;

import io.github.tsndre.minijava.common.response.ApiError;
import io.github.tsndre.minijava.common.response.ApiResponse;
import io.github.tsndre.minijava.common.response.Meta;
import io.github.tsndre.minijava.common.response.Pagination;
import io.github.tsndre.minijava.store.constant.OrderStatus;
import io.github.tsndre.minijava.store.constant.PaymentMethod;
import io.github.tsndre.minijava.store.constant.PaymentStatus;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.request.AddCartItemRequest;
import io.github.tsndre.minijava.store.dto.request.LoginRequest;
import io.github.tsndre.minijava.store.model.Cart;
import io.github.tsndre.minijava.store.model.CartItem;
import io.github.tsndre.minijava.store.model.Order;
import io.github.tsndre.minijava.store.model.Payment;
import io.github.tsndre.minijava.store.model.Product;
import io.github.tsndre.minijava.store.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The JSON the service reads and writes must match the Go service's encoding/json byte for byte. */
class GoJsonTest {

    private static JsonMapper json;

    @BeforeAll
    static void mapper() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(JacksonConfig.class)
                .run(context -> json = context.getBean(JsonMapper.class));
    }

    private static final Meta META = new Meta("req-1", "2026-10-07T03:04:05Z", null);

    @Test
    void errorEnvelopeOmitsDataAndEmptyField() {
        ApiResponse body = new ApiResponse(null, META, List.of(ApiError.of("NOT_FOUND", "not found")));

        assertThat(json.writeValueAsString(body)).isEqualTo(
                "{\"meta\":{\"request_id\":\"req-1\",\"timestamp\":\"2026-10-07T03:04:05Z\"},"
                        + "\"errors\":[{\"code\":\"NOT_FOUND\",\"message\":\"not found\"}]}");
    }

    @Test
    void successEnvelopeOmitsErrorsAndKeepsEmptyLists() {
        ApiResponse body = new ApiResponse(List.of(), META.withPagination(new Pagination(1, 10, 0, 0)), null);

        assertThat(json.writeValueAsString(body)).isEqualTo(
                "{\"data\":[],\"meta\":{\"request_id\":\"req-1\",\"timestamp\":\"2026-10-07T03:04:05Z\","
                        + "\"pagination\":{\"current_page\":1,\"per_page\":10,\"total_items\":0,\"total_pages\":0}}}");
    }

    @Test
    void fieldErrorsCarryTheField() {
        assertThat(json.writeValueAsString(ApiError.field("VALIDATION_ERROR", "email", "is required")))
                .isEqualTo("{\"code\":\"VALIDATION_ERROR\",\"field\":\"email\",\"message\":\"is required\"}");
    }

    @Test
    void htmlCharactersAndLineSeparatorsAreEscapedLikeGo() {
        assertThat(json.writeValueAsString(Map.of("name", "<b>Tom & Jerry</b> /x")))
                .isEqualTo("{\"name\":\"\\u003cb\\u003eTom \\u0026 Jerry\\u003c/b\\u003e\\u2028/x\"}");
        assertThat(json.writeValueAsString(Map.of("c", "\u0001\t"))).isEqualTo("{\"c\":\"\\u0001\\t\"}");
    }

    @Test
    void decimalsAreStringsWithoutTrailingZeros() {
        Product product = Product.builder().price(new BigDecimal("15000.50")).build();

        assertThat(json.writeValueAsString(product)).contains("\"price\":\"15000.5\"");
        assertThat(json.writeValueAsString(Map.of("total", new BigDecimal("20000.00")))).isEqualTo("{\"total\":\"20000\"}");
    }

    @Test
    void timestampsKeepTheirOffsetAndDropTrailingZeros() {
        OffsetDateTime time = OffsetDateTime.of(2026, 10, 7, 10, 11, 12, 120_000_000, ZoneOffset.ofHours(7));

        assertThat(json.writeValueAsString(Map.of("t", time))).isEqualTo("{\"t\":\"2026-10-07T10:11:12.12+07:00\"}");
        assertThat(json.writeValueAsString(Map.of("t", time.withNano(0)))).isEqualTo("{\"t\":\"2026-10-07T10:11:12+07:00\"}");
        assertThat(json.writeValueAsString(Map.of("t", Cart.ZERO_TIME))).isEqualTo("{\"t\":\"0001-01-01T00:00:00Z\"}");
    }

    @Test
    void orderResponseShapesMatchGo() {
        Order withoutItems = Order.builder().id(UUID.randomUUID()).totalAmount(BigDecimal.TEN).build();
        String withoutItemsJson = json.writeValueAsString(withoutItems.toResponse());
        assertThat(withoutItemsJson).contains("\"items\":null").doesNotContain("\"payment\"");

        Order withPayment = Order.builder().id(UUID.randomUUID()).totalAmount(BigDecimal.TEN)
                .payment(Payment.builder().amount(BigDecimal.TEN).build()).build();
        assertThat(json.writeValueAsString(withPayment.toResponse())).contains("\"paid_at\":null");
    }

    @Test
    void enumsAreWrittenAsTheirValues() {
        Order order = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).totalAmount(BigDecimal.TEN)
                .payment(Payment.builder().method(PaymentMethod.MOCK).status(PaymentStatus.SUCCESS)
                        .amount(BigDecimal.TEN).build())
                .build();
        assertThat(json.writeValueAsString(order.toResponse()))
                .contains("\"status\":\"paid\"", "\"method\":\"mock\"", "\"status\":\"success\"");
        assertThat(json.writeValueAsString(User.builder().role(Role.SELLER).build().toResponse()))
                .contains("\"role\":\"seller\"");
    }

    @Test
    void cachedModelsRoundTripWithTheirOffset() {
        OffsetDateTime updatedAt = OffsetDateTime.of(2026, 10, 7, 10, 0, 0, 5, ZoneOffset.ofHours(7));
        Cart cart = new Cart(UUID.randomUUID());
        cart.getItems().add(CartItem.builder().productId(UUID.randomUUID()).name("Mug").price(new BigDecimal("9.90"))
                .quantity(2).imageUrl("").build());
        cart.setUpdatedAt(updatedAt);

        String cached = json.writeValueAsString(cart);
        assertThat(cached).startsWith("{\"user_id\":").contains("\"price\":\"9.9\"");

        Cart restored = json.readValue(cached, Cart.class);
        assertThat(restored.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(restored.getUpdatedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(7));
        assertThat(restored.getItems().getFirst().getPrice()).isEqualByComparingTo("9.9");
    }

    @Test
    void namesMatchCaseInsensitivelyAndUnknownFieldsAreIgnored() {
        LoginRequest req = json.readValue("{\"EMAIL\":\"a@b.co\",\"Password\":\"x\",\"extra\":{\"y\":[1]}}",
                LoginRequest.class);

        assertThat(req.email()).isEqualTo("a@b.co");
        assertThat(req.password()).isEqualTo("x");
    }

    @Test
    void missingAndNullFieldsGetZeroValues() {
        AddCartItemRequest req = json.readValue("{\"product_id\":null}", AddCartItemRequest.class);

        assertThat(req.productId()).isEmpty();
        assertThat(req.quantity()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"email\":123}",
            "{\"email\":true}",
            "{\"email\":{}}",
            "{\"email\":[\"a\"]}",
    })
    void aNonStringForAStringFieldIsRejected(String body) {
        assertThatThrownBy(() -> json.readValue(body, LoginRequest.class)).isInstanceOf(JacksonException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"quantity\":\"2\"}",
            "{\"quantity\":2.0}",
            "{\"quantity\":2.5}",
            "{\"quantity\":1e2}",
            "{\"quantity\":true}",
            "{\"quantity\":99999999999999999999}",
    })
    void anythingButAnIntegerForAnIntegerFieldIsRejected(String body) {
        assertThatThrownBy(() -> json.readValue(body, AddCartItemRequest.class)).isInstanceOf(JacksonException.class);
    }

    @Test
    void largeIntegersFitLikeGoInt64() {
        assertThat(json.readValue("{\"quantity\":3000000000}", AddCartItemRequest.class).quantity()).isEqualTo(3_000_000_000L);
    }
}
