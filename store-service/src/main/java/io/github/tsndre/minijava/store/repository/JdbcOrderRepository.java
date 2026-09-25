package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.constant.CacheKey;
import io.github.tsndre.minijava.store.constant.OrderStatus;
import io.github.tsndre.minijava.store.constant.PaymentStatus;
import io.github.tsndre.minijava.store.model.Order;
import io.github.tsndre.minijava.store.model.OrderItem;
import io.github.tsndre.minijava.store.model.Payment;
import io.github.tsndre.minijava.store.model.Product;
import io.github.tsndre.minijava.store.repository.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Repository
@RequiredArgsConstructor
public class JdbcOrderRepository implements OrderRepository {

    static final String ORDER_COLUMNS =
            "id, user_id, store_id, status, total_amount, shipping_address, created_at, updated_at";
    static final String ORDER_ITEM_COLUMNS = "id, order_id, product_id, quantity, price, created_at";
    static final String PAYMENT_COLUMNS = "id, order_id, method, status, amount, paid_at, created_at, updated_at";

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final Cache cache;

    @Override
    public List<Order> createOrdersWithStock(List<StockReservation> reservations,
                                             Function<List<Product>, List<Order>> build) {
        List<Order> orders;
        try {
            orders = transactions.execute(status -> {
                List<Product> reserved = new ArrayList<>(reservations.size());
                for (StockReservation res : reservations) {
                    // The conditional decrement takes a row lock, so concurrent checkouts of the
                    // same product serialize here and can never drive stock below zero.
                    Optional<Product> product = jdbc.sql("""
                                    UPDATE products
                                    SET stock = stock - ?, updated_at = NOW()
                                    WHERE id = ? AND stock >= ?
                                    RETURNING %s""".formatted(JdbcProductRepository.COLUMNS))
                            .params(res.quantity(), res.productId(), res.quantity())
                            .query(RowMappers.PRODUCT)
                            .optional();
                    if (product.isEmpty()) {
                        throw reservationError(res.productId());
                    }
                    reserved.add(product.get());
                }

                List<Order> stored = new ArrayList<>();
                for (Order order : build.apply(reserved)) {
                    stored.add(insertOrder(order));
                }
                return stored;
            });
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }

        for (StockReservation res : reservations) {
            invalidateProduct(res.productId());
        }
        return orders;
    }

    /** Explains why a conditional stock decrement matched no row. */
    private RuntimeException reservationError(UUID productId) {
        Optional<String> name = jdbc.sql("SELECT name FROM products WHERE id = ?")
                .param(productId)
                .query(String.class)
                .optional();
        if (name.isEmpty()) {
            return new ProductNotFoundException(productId);
        }
        return new StockUnavailableException(productId, name.get());
    }

    /** Inserts the order with its items and payment, and returns them as stored, with their IDs and timestamps. */
    private Order insertOrder(Order order) {
        Order stored = jdbc.sql("""
                        INSERT INTO orders (user_id, store_id, status, total_amount, shipping_address)
                        VALUES (?, ?, ?, ?, ?)
                        RETURNING %s""".formatted(ORDER_COLUMNS))
                .params(order.getUserId(), order.getStoreId(), order.getStatus().value(), order.getTotalAmount(),
                        order.getShippingAddress())
                .query(RowMappers.ORDER)
                .single();

        for (OrderItem item : order.getOrderItems()) {
            stored.getOrderItems().add(jdbc.sql("""
                            INSERT INTO order_items (order_id, product_id, quantity, price)
                            VALUES (?, ?, ?, ?)
                            RETURNING %s""".formatted(ORDER_ITEM_COLUMNS))
                    .params(stored.getId(), item.getProductId(), item.getQuantity(), item.getPrice())
                    .query(RowMappers.ORDER_ITEM)
                    .single());
        }

        Payment payment = order.getPayment();
        if (payment != null) {
            stored.setPayment(jdbc.sql("""
                            INSERT INTO payments (order_id, method, status, amount)
                            VALUES (?, ?, ?, ?)
                            RETURNING %s""".formatted(PAYMENT_COLUMNS))
                    .params(stored.getId(), payment.getMethod().value(), payment.getStatus().value(), payment.getAmount())
                    .query(RowMappers.PAYMENT)
                    .single());
        }
        return stored;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        Optional<Order> order = jdbc.sql("SELECT " + ORDER_COLUMNS + " FROM orders WHERE id = ?")
                .param(id)
                .query(RowMappers.ORDER)
                .optional();
        order.ifPresent(o -> loadDetails(List.of(o)));
        return order;
    }

    @Override
    public PageResult<Order> findByUserId(UUID userId, long page, int perPage) {
        return findPage("user_id", userId, page, perPage);
    }

    @Override
    public PageResult<Order> findByStoreId(UUID storeId, long page, int perPage) {
        return findPage("store_id", storeId, page, perPage);
    }

    /**
     * A page of the orders whose column equals value, newest first, with their items and payments.
     * column is a literal from the callers above, never user input.
     */
    private PageResult<Order> findPage(String column, UUID value, long page, int perPage) {
        long total = jdbc.sql("SELECT COUNT(*) FROM orders WHERE " + column + " = ?")
                .param(value)
                .query(Long.class)
                .single();

        List<Order> orders = jdbc.sql("""
                        SELECT %s
                        FROM orders
                        WHERE %s = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?""".formatted(ORDER_COLUMNS, column))
                .params(value, perPage, (page - 1) * perPage)
                .query(RowMappers.ORDER)
                .list();
        loadDetails(orders);
        return new PageResult<>(orders, total);
    }

    /** Fills in the items and payment of each order, with one query for all items and one for all payments. */
    private void loadDetails(List<Order> orders) {
        if (orders.isEmpty()) {
            return;
        }
        Map<UUID, Order> byId = new LinkedHashMap<>();
        for (Order order : orders) {
            byId.put(order.getId(), order);
        }

        jdbc.sql("SELECT " + ORDER_ITEM_COLUMNS + " FROM order_items WHERE order_id IN (:ids) ORDER BY product_id")
                .param("ids", byId.keySet())
                .query(RowMappers.ORDER_ITEM)
                .list()
                .forEach(item -> byId.get(item.getOrderId()).getOrderItems().add(item));

        jdbc.sql("SELECT " + PAYMENT_COLUMNS + " FROM payments WHERE order_id IN (:ids)")
                .param("ids", byId.keySet())
                .query(RowMappers.PAYMENT)
                .list()
                .forEach(payment -> byId.get(payment.getOrderId()).setPayment(payment));
    }

    @Override
    public List<Order> findStalePending(OffsetDateTime createdBefore, int limit) {
        return jdbc.sql("""
                        SELECT %s
                        FROM orders
                        WHERE status = ? AND created_at < ?
                        ORDER BY created_at ASC
                        LIMIT ?""".formatted(ORDER_COLUMNS))
                .params(OrderStatus.PENDING.value(), createdBefore, limit)
                .query(RowMappers.ORDER)
                .list();
    }

    @Override
    public boolean updateStatusIfCurrent(UUID id, OrderStatus fromStatus, OrderStatus toStatus) {
        try {
            return jdbc.sql("UPDATE orders SET status = ?, updated_at = NOW() WHERE id = ? AND status = ?")
                    .params(toStatus.value(), id, fromStatus.value())
                    .update() > 0;
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }

    @Override
    public boolean cancelAndRestock(UUID id, OrderStatus fromStatus) {
        return cancelAndRestock(id, fromStatus, PaymentStatus.CANCELLED);
    }

    @Override
    public boolean markPaymentFailed(UUID orderId) {
        return cancelAndRestock(orderId, OrderStatus.PENDING, PaymentStatus.FAILED);
    }

    private boolean cancelAndRestock(UUID id, OrderStatus fromStatus, PaymentStatus pendingPaymentStatus) {
        List<OrderItem> items = new ArrayList<>();
        boolean cancelled;
        try {
            cancelled = Boolean.TRUE.equals(transactions.execute(status -> {
                int changed = jdbc.sql("UPDATE orders SET status = ?, updated_at = NOW() WHERE id = ? AND status = ?")
                        .params(OrderStatus.CANCELLED.value(), id, fromStatus.value())
                        .update();
                if (changed == 0) {
                    return false;
                }

                // Restock in product_id order, the same order checkout reserves in, so a
                // concurrent checkout and cancel cannot deadlock on product row locks.
                items.addAll(jdbc.sql("SELECT " + ORDER_ITEM_COLUMNS
                                + " FROM order_items WHERE order_id = ? ORDER BY product_id")
                        .param(id)
                        .query(RowMappers.ORDER_ITEM)
                        .list());
                for (OrderItem item : items) {
                    jdbc.sql("UPDATE products SET stock = stock + ?, updated_at = NOW() WHERE id = ?")
                            .params(item.getQuantity(), item.getProductId())
                            .update();
                }

                jdbc.sql("UPDATE payments SET status = ?, updated_at = NOW() WHERE order_id = ? AND status = ?")
                        .params(pendingPaymentStatus.value(), id, PaymentStatus.PENDING.value())
                        .update();
                return true;
            }));
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }

        for (OrderItem item : items) {
            invalidateProduct(item.getProductId());
        }
        return cancelled;
    }

    @Override
    public boolean markPaymentSucceeded(UUID orderId) {
        try {
            return Boolean.TRUE.equals(transactions.execute(status -> {
                int changed = jdbc.sql("UPDATE orders SET status = ?, updated_at = NOW() WHERE id = ? AND status = ?")
                        .params(OrderStatus.PAID.value(), orderId, OrderStatus.PENDING.value())
                        .update();
                if (changed == 0) {
                    return false;
                }

                jdbc.sql("""
                                UPDATE payments
                                SET status = ?, paid_at = NOW(), updated_at = NOW()
                                WHERE order_id = ? AND status = ?""")
                        .params(PaymentStatus.SUCCESS.value(), orderId, PaymentStatus.PENDING.value())
                        .update();
                return true;
            }));
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }

    private void invalidateProduct(UUID productId) {
        cache.delete(CacheKey.PRODUCT.formatted(productId));
    }
}
