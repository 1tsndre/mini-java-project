package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.constant.NsqTopic;
import io.github.tsndre.minijava.store.constant.OrderStatus;
import io.github.tsndre.minijava.store.constant.PaymentMethod;
import io.github.tsndre.minijava.store.constant.PaymentStatus;
import io.github.tsndre.minijava.store.dto.response.OrderResponse;
import io.github.tsndre.minijava.store.model.Cart;
import io.github.tsndre.minijava.store.model.CartItem;
import io.github.tsndre.minijava.store.model.Order;
import io.github.tsndre.minijava.store.model.OrderItem;
import io.github.tsndre.minijava.store.model.Payment;
import io.github.tsndre.minijava.store.model.Product;
import io.github.tsndre.minijava.store.model.Store;
import io.github.tsndre.minijava.store.pagination.Pages;
import io.github.tsndre.minijava.store.repository.CartRepository;
import io.github.tsndre.minijava.store.repository.OrderRepository;
import io.github.tsndre.minijava.store.repository.PageResult;
import io.github.tsndre.minijava.store.repository.ProductNotFoundException;
import io.github.tsndre.minijava.store.repository.StockReservation;
import io.github.tsndre.minijava.store.repository.StockUnavailableException;
import io.github.tsndre.minijava.store.repository.StoreRepository;
import io.github.tsndre.minijava.store.service.exception.ForbiddenException;
import io.github.tsndre.minijava.store.service.exception.InsufficientStockException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.InvalidStatusException;
import io.github.tsndre.minijava.store.service.exception.NotFoundException;
import io.github.tsndre.minijava.store.service.exception.ValidationException;
import io.github.tsndre.minijava.store.util.GoDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final StoreRepository storeRepository;
    private final CartLock cartLock;
    private final MessagePublisher publisher;
    private final JsonMapper jsonMapper;

    public List<OrderResponse> checkout(UUID userId, String shippingAddress) {
        // Hold the cart lock from reading the cart until it is cleared: a double-submitted checkout
        // then finds the cart already empty instead of ordering it twice, and an item added
        // meanwhile is not wiped by deleteCart.
        try (CartLock.Handle ignored = cartLock.lock(userId)) {
            Cart cart;
            try {
                cart = cartRepository.getCart(userId);
            } catch (RuntimeException e) {
                throw new InternalException("failed to load cart");
            }
            if (cart.getItems().isEmpty()) {
                throw new ValidationException("cart is empty");
            }

            // A stable product order makes concurrent checkouts lock product rows in the same
            // sequence, which avoids deadlocks between them.
            List<CartItem> items = new ArrayList<>(cart.getItems());
            items.sort(Comparator.comparing(item -> item.getProductId().toString()));

            List<StockReservation> reservations = items.stream()
                    .map(item -> new StockReservation(item.getProductId(), item.getQuantity()))
                    .toList();

            List<Order> orders;
            try {
                orders = orderRepository.createOrdersWithStock(reservations,
                        reserved -> buildOrdersByStore(userId, shippingAddress, items, reserved));
            } catch (StockUnavailableException e) {
                throw new InsufficientStockException(
                        CartService.INSUFFICIENT_STOCK + " for product " + e.getProductName());
            } catch (ProductNotFoundException e) {
                throw new NotFoundException(e.getMessage());
            } catch (RuntimeException e) {
                log.error("failed to create orders", e);
                throw new InternalException("failed to process checkout");
            }

            try {
                cartRepository.deleteCart(userId);
            } catch (RuntimeException e) {
                log.atError().setCause(e).addKeyValue("user_id", userId).log("failed to clear cart after checkout");
            }

            // A failed publish is not fatal: the order stays pending and the payment retry
            // worker republishes it later.
            orders.forEach(this::publishOrderCreated);

            log.atInfo()
                    .addKeyValue("user_id", userId)
                    .addKeyValue("count", orders.size())
                    .log("orders created");

            return orders.stream().map(Order::toResponse).toList();
        }
    }

    /**
     * Splits the checked-out items into one pending order per store, priced at the reserved
     * products' current price. items and reserved must be in the same order.
     */
    static List<Order> buildOrdersByStore(UUID userId, String shippingAddress, List<CartItem> items,
                                          List<Product> reserved) {
        Map<UUID, Order> ordersByStore = new LinkedHashMap<>();

        for (int i = 0; i < reserved.size(); i++) {
            Product product = reserved.get(i);
            Order order = ordersByStore.computeIfAbsent(product.getStoreId(), storeId -> Order.builder()
                    .userId(userId)
                    .storeId(storeId)
                    .status(OrderStatus.PENDING)
                    .totalAmount(BigDecimal.ZERO)
                    .shippingAddress(shippingAddress)
                    .build());

            int quantity = items.get(i).getQuantity();
            order.getOrderItems().add(OrderItem.builder()
                    .productId(product.getId())
                    .quantity(quantity)
                    .price(product.getPrice())
                    .build());
            order.setTotalAmount(order.getTotalAmount().add(product.getPrice().multiply(BigDecimal.valueOf(quantity))));
        }

        for (Order order : ordersByStore.values()) {
            order.setPayment(Payment.builder()
                    .method(PaymentMethod.MOCK)
                    .status(PaymentStatus.PENDING)
                    .amount(order.getTotalAmount())
                    .build());
        }
        return new ArrayList<>(ordersByStore.values());
    }

    private boolean publishOrderCreated(Order order) {
        // Sorted keys, like Go's encoding of a map.
        Map<String, String> payload = new TreeMap<>(Map.of(
                "order_id", order.getId().toString(),
                "user_id", order.getUserId().toString(),
                "total_amount", GoDecimal.format(order.getTotalAmount())));

        byte[] message;
        try {
            message = jsonMapper.writeValueAsBytes(payload);
        } catch (RuntimeException e) {
            log.error("failed to marshal order.created payload", e);
            return false;
        }
        try {
            publisher.publish(NsqTopic.ORDER_CREATED, message);
        } catch (RuntimeException e) {
            log.atError().setCause(e).addKeyValue("order_id", order.getId()).log("failed to publish order.created");
            return false;
        }
        return true;
    }

    /**
     * Republishes order.created for orders still pending after {@code olderThan}, covering
     * publishes that failed or were lost.
     *
     * @return how many orders were republished
     */
    public int retryPendingPayments(Duration olderThan, int limit) {
        List<Order> orders = orderRepository.findStalePending(OffsetDateTime.now().minus(olderThan), limit);

        int published = 0;
        for (Order order : orders) {
            if (publishOrderCreated(order)) {
                published++;
            }
        }
        return published;
    }

    public PageResult<OrderResponse> getOrders(UUID userId, long page, long perPage) {
        Pages.Page normalized = Pages.normalize(page, perPage);

        PageResult<Order> orders;
        try {
            orders = orderRepository.findByUserId(userId, normalized.page(), normalized.perPage());
        } catch (RuntimeException e) {
            throw new InternalException("failed to fetch orders");
        }

        return new PageResult<>(orders.items().stream().map(Order::toResponse).toList(), orders.total());
    }

    public OrderResponse getOrderById(UUID userId, UUID id) {
        Order order = findOrder(id);

        if (!order.getUserId().equals(userId)) {
            throw new ForbiddenException("forbidden");
        }

        return order.toResponse();
    }

    public void cancelOrder(UUID userId, UUID id) {
        Order order = findOrder(id);

        if (!order.getUserId().equals(userId)) {
            throw new ForbiddenException("forbidden");
        }

        if (!order.getStatus().isCancellable()) {
            throw new InvalidStatusException("cannot cancel order with status " + order.getStatus().value());
        }

        boolean cancelled;
        try {
            cancelled = orderRepository.cancelAndRestock(id, order.getStatus());
        } catch (RuntimeException e) {
            log.atError().setCause(e).addKeyValue("order_id", id).log("failed to cancel order");
            throw new InternalException("failed to cancel order");
        }
        if (!cancelled) {
            throw new InvalidStatusException("cannot cancel order, status changed");
        }

        log.atInfo().addKeyValue("order_id", id).log("order cancelled");
    }

    public void updateOrderStatus(UUID sellerId, UUID id, String status) {
        Order order = findOrder(id);
        OrderStatus current = order.getStatus();

        List<OrderStatus> allowed = current.transitions();
        if (allowed.isEmpty()) {
            throw new InvalidStatusException(
                    "cannot transition from status " + current.value() + ": invalid status transition");
        }
        // The requested status is raw user input, so it is matched by value instead of parsed.
        Optional<OrderStatus> next = allowed.stream().filter(s -> s.value().equals(status)).findFirst();
        if (next.isEmpty()) {
            throw new InvalidStatusException("invalid status transition from " + current.value() + " to " + status);
        }

        Store store;
        try {
            store = storeRepository.findByUserId(sellerId).orElseThrow();
        } catch (RuntimeException e) {
            throw new NotFoundException("store not found");
        }

        if (!order.getStoreId().equals(store.getId())) {
            throw new ForbiddenException("forbidden: order does not belong to your store");
        }

        boolean updated;
        try {
            updated = orderRepository.updateStatusIfCurrent(id, current, next.get());
        } catch (RuntimeException e) {
            log.error("failed to update order status", e);
            throw new InternalException("failed to update order status");
        }
        if (!updated) {
            throw new InvalidStatusException("cannot update order, status changed");
        }
    }

    public PageResult<OrderResponse> getSellerOrders(UUID userId, long page, long perPage) {
        Pages.Page normalized = Pages.normalize(page, perPage);

        Store store;
        try {
            store = storeRepository.findByUserId(userId).orElseThrow();
        } catch (RuntimeException e) {
            throw new NotFoundException("store not found");
        }

        PageResult<Order> orders;
        try {
            orders = orderRepository.findByStoreId(store.getId(), normalized.page(), normalized.perPage());
        } catch (RuntimeException e) {
            throw new InternalException("failed to fetch orders");
        }

        return new PageResult<>(orders.items().stream().map(Order::toResponse).toList(), orders.total());
    }

    /**
     * Applies a payment result from the payment service. A result for an order that is no longer
     * pending is ignored.
     *
     * @throws RuntimeException if the database update failed, so the message is retried
     */
    public void processPaymentResult(UUID orderId, boolean success) {
        if (success) {
            boolean paid;
            try {
                paid = orderRepository.markPaymentSucceeded(orderId);
            } catch (RuntimeException e) {
                log.atError().setCause(e).addKeyValue("order_id", orderId).log("failed to mark order as paid");
                throw e;
            }
            if (!paid) {
                log.atWarn().addKeyValue("order_id", orderId).log("ignoring payment success for non-pending order");
                return;
            }
            log.atInfo().addKeyValue("order_id", orderId).log("payment success");
            return;
        }

        boolean cancelled;
        try {
            cancelled = orderRepository.markPaymentFailed(orderId);
        } catch (RuntimeException e) {
            log.atError().setCause(e).addKeyValue("order_id", orderId)
                    .log("failed to cancel order after payment failure");
            throw e;
        }
        if (!cancelled) {
            log.atWarn().addKeyValue("order_id", orderId).log("ignoring payment failure for non-pending order");
            return;
        }
        log.atInfo().addKeyValue("order_id", orderId).log("payment failed, order cancelled");
    }

    private Order findOrder(UUID id) {
        try {
            return orderRepository.findById(id).orElseThrow();
        } catch (RuntimeException e) {
            throw new NotFoundException("order not found");
        }
    }
}
