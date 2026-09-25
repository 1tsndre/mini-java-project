package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.constant.OrderStatus;
import io.github.tsndre.minijava.store.constant.PaymentMethod;
import io.github.tsndre.minijava.store.constant.PaymentStatus;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.model.CartItem;
import io.github.tsndre.minijava.store.model.Category;
import io.github.tsndre.minijava.store.model.Order;
import io.github.tsndre.minijava.store.model.OrderItem;
import io.github.tsndre.minijava.store.model.Payment;
import io.github.tsndre.minijava.store.model.Product;
import io.github.tsndre.minijava.store.model.Review;
import io.github.tsndre.minijava.store.model.Store;
import io.github.tsndre.minijava.store.model.User;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

final class RowMappers {

    static final RowMapper<User> USER = (rs, i) -> User.builder()
            .id(uuid(rs, "id"))
            .email(text(rs, "email"))
            .password(text(rs, "password"))
            .name(text(rs, "name"))
            .role(Role.fromValue(rs.getString("role")))
            .createdAt(time(rs, "created_at"))
            .updatedAt(time(rs, "updated_at"))
            .build();

    static final RowMapper<Store> STORE = (rs, i) -> Store.builder()
            .id(uuid(rs, "id"))
            .userId(uuid(rs, "user_id"))
            .name(text(rs, "name"))
            .description(text(rs, "description"))
            .logoUrl(text(rs, "logo_url"))
            .createdAt(time(rs, "created_at"))
            .updatedAt(time(rs, "updated_at"))
            .build();

    static final RowMapper<Category> CATEGORY = (rs, i) -> Category.builder()
            .id(uuid(rs, "id"))
            .name(text(rs, "name"))
            .createdAt(time(rs, "created_at"))
            .updatedAt(time(rs, "updated_at"))
            .build();

    static final RowMapper<Product> PRODUCT = (rs, i) -> Product.builder()
            .id(uuid(rs, "id"))
            .storeId(uuid(rs, "store_id"))
            .categoryId(uuid(rs, "category_id"))
            .name(text(rs, "name"))
            .description(text(rs, "description"))
            .price(rs.getBigDecimal("price"))
            .stock(rs.getInt("stock"))
            .imageUrl(text(rs, "image_url"))
            .createdAt(time(rs, "created_at"))
            .updatedAt(time(rs, "updated_at"))
            .build();

    /** A cart line joined with its product's current name, price and image. */
    static final RowMapper<CartItem> CART_ITEM = (rs, i) -> CartItem.builder()
            .productId(uuid(rs, "product_id"))
            .quantity(rs.getInt("quantity"))
            .name(text(rs, "name"))
            .price(rs.getBigDecimal("price"))
            .imageUrl(text(rs, "image_url"))
            .build();

    static final RowMapper<Order> ORDER = (rs, i) -> Order.builder()
            .id(uuid(rs, "id"))
            .userId(uuid(rs, "user_id"))
            .storeId(uuid(rs, "store_id"))
            .status(OrderStatus.fromValue(rs.getString("status")))
            .totalAmount(rs.getBigDecimal("total_amount"))
            .shippingAddress(text(rs, "shipping_address"))
            .createdAt(time(rs, "created_at"))
            .updatedAt(time(rs, "updated_at"))
            .build();

    static final RowMapper<OrderItem> ORDER_ITEM = (rs, i) -> OrderItem.builder()
            .id(uuid(rs, "id"))
            .orderId(uuid(rs, "order_id"))
            .productId(uuid(rs, "product_id"))
            .quantity(rs.getInt("quantity"))
            .price(rs.getBigDecimal("price"))
            .createdAt(time(rs, "created_at"))
            .build();

    static final RowMapper<Payment> PAYMENT = (rs, i) -> Payment.builder()
            .id(uuid(rs, "id"))
            .orderId(uuid(rs, "order_id"))
            .method(PaymentMethod.fromValue(rs.getString("method")))
            .status(PaymentStatus.fromValue(rs.getString("status")))
            .amount(rs.getBigDecimal("amount"))
            .paidAt(time(rs, "paid_at"))
            .createdAt(time(rs, "created_at"))
            .updatedAt(time(rs, "updated_at"))
            .build();

    static final RowMapper<Review> REVIEW = (rs, i) -> Review.builder()
            .id(uuid(rs, "id"))
            .userId(uuid(rs, "user_id"))
            .productId(uuid(rs, "product_id"))
            .rating(rs.getInt("rating"))
            .comment(text(rs, "comment"))
            .createdAt(time(rs, "created_at"))
            .updatedAt(time(rs, "updated_at"))
            .build();

    /** A review joined with its author's name. */
    static final RowMapper<Review> REVIEW_WITH_USER_NAME = (rs, i) -> {
        Review review = REVIEW.mapRow(rs, i);
        review.setUserName(text(rs, "user_name"));
        return review;
    };

    private RowMappers() {
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    /** Optional text columns default to '' in the schema; NULL reads as an empty string. */
    private static String text(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? "" : value;
    }

    /** Timestamps in the server's time zone, the way the Go service reports them. */
    static OffsetDateTime time(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
