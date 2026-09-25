package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.constant.OrderStatus;
import io.github.tsndre.minijava.store.model.Order;
import io.github.tsndre.minijava.store.model.Product;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface OrderRepository {

    /**
     * Decrements stock for every reservation and inserts the orders returned by {@code build} in a
     * single transaction. {@code build} receives the reserved products (post-decrement, in
     * reservation order). Nothing is written unless every reservation succeeds.
     *
     * @return the stored orders, with their items and payments
     * @throws ProductNotFoundException  if a reserved product no longer exists
     * @throws StockUnavailableException if a product has less stock than requested
     */
    List<Order> createOrdersWithStock(List<StockReservation> reservations, Function<List<Product>, List<Order>> build);

    Optional<Order> findById(UUID id);

    PageResult<Order> findByUserId(UUID userId, long page, int perPage);

    PageResult<Order> findByStoreId(UUID storeId, long page, int perPage);

    List<Order> findStalePending(OffsetDateTime createdBefore, int limit);

    boolean updateStatusIfCurrent(UUID id, OrderStatus fromStatus, OrderStatus toStatus);

    /**
     * Moves the order from {@code fromStatus} to cancelled, returns its items to stock and closes a
     * still-pending payment, all in one transaction. Reports false if the order was no longer in
     * {@code fromStatus}.
     */
    boolean cancelAndRestock(UUID id, OrderStatus fromStatus);

    /** Moves a pending order to paid and records the payment as successful; false if it was no longer pending. */
    boolean markPaymentSucceeded(UUID orderId);

    /** Cancels a pending order, restocks its items and records the payment as failed; false if it was no longer pending. */
    boolean markPaymentFailed(UUID orderId);
}
