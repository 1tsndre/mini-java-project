package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.constant.NsqTopic;
import io.github.tsndre.minijava.store.constant.OrderStatus;
import io.github.tsndre.minijava.store.constant.PaymentStatus;
import io.github.tsndre.minijava.store.dto.response.OrderResponse;
import io.github.tsndre.minijava.store.model.Cart;
import io.github.tsndre.minijava.store.model.CartItem;
import io.github.tsndre.minijava.store.model.Order;
import io.github.tsndre.minijava.store.model.Product;
import io.github.tsndre.minijava.store.model.Store;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    static final class FakePublisher implements MessagePublisher {

        final List<String> messages = new ArrayList<>();
        RuntimeException error;

        @Override
        public void publish(String topic, byte[] body) {
            if (error != null) {
                throw error;
            }
            messages.add(topic + " " + new String(body, StandardCharsets.UTF_8));
        }
    }

    private final UUID userId = UUID.randomUUID();
    private final FakePublisher publisher = new FakePublisher();

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private CartLock cartLock;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        when(cartLock.lock(any())).thenReturn(() -> { });
        orderService = new OrderService(orderRepository, cartRepository, storeRepository, cartLock, publisher,
                JsonMapper.builder().build());
    }

    @Nested
    class Checkout {

        private static final String ADDRESS = "Jl. Test No. 1, Jakarta";

        private final UUID storeA = UUID.randomUUID();
        private final UUID storeB = UUID.randomUUID();
        private final UUID productA1 = UUID.randomUUID();
        private final UUID productA2 = UUID.randomUUID();
        private final UUID productB1 = UUID.randomUUID();
        private final Map<UUID, Product> catalog = Map.of(
                productA1, Product.builder().id(productA1).storeId(storeA).price(new BigDecimal("100")).build(),
                productA2, Product.builder().id(productA2).storeId(storeA).price(new BigDecimal("10")).build(),
                productB1, Product.builder().id(productB1).storeId(storeB).price(new BigDecimal("50")).build());
        private final Map<UUID, Integer> wantQuantity = Map.of(productA1, 2, productA2, 3, productB1, 1);

        private Cart cart() {
            Cart cart = new Cart(userId);
            cart.setItems(new ArrayList<>(List.of(
                    CartItem.builder().productId(productA1).quantity(2).build(),
                    CartItem.builder().productId(productB1).quantity(1).build(),
                    CartItem.builder().productId(productA2).quantity(3).build())));
            return cart;
        }

        /**
         * Simulates a successful stock reservation by handing the reserved products to the
         * service's build callback, as the real repository does. It also checks the reservations:
         * sorted by product ID (the lock order that keeps concurrent checkouts and cancels from
         * deadlocking) with the cart quantities.
         */
        @SuppressWarnings("unchecked")
        private void reserveAll() {
            when(orderRepository.createOrdersWithStock(any(), any())).thenAnswer(invocation -> {
                List<StockReservation> reservations = invocation.getArgument(0);
                Function<List<Product>, List<Order>> build = invocation.getArgument(1);

                assertThat(reservations).hasSize(wantQuantity.size());
                for (int i = 0; i < reservations.size(); i++) {
                    StockReservation res = reservations.get(i);
                    assertThat(res.quantity()).isEqualTo(wantQuantity.get(res.productId()));
                    if (i > 0) {
                        assertThat(reservations.get(i - 1).productId().toString())
                                .as("reservations must be sorted by product ID")
                                .isLessThan(res.productId().toString());
                    }
                }

                List<Order> orders = build.apply(reservations.stream().map(r -> catalog.get(r.productId())).toList());
                orders.forEach(order -> order.setId(UUID.randomUUID()));
                return orders;
            });
        }

        @Test
        void cartLoadFails() {
            when(cartRepository.getCart(userId)).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> orderService.checkout(userId, ADDRESS))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to load cart");
        }

        @Test
        void cartIsEmpty() {
            when(cartRepository.getCart(userId)).thenReturn(new Cart(userId));

            assertThatThrownBy(() -> orderService.checkout(userId, ADDRESS))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("cart is empty");
        }

        @Test
        void successSplitsIntoOneOrderPerStore() {
            when(cartRepository.getCart(userId)).thenReturn(cart());
            reserveAll();

            List<OrderResponse> orders = orderService.checkout(userId, ADDRESS);

            assertThat(orders).hasSize(2);
            assertThat(publisher.messages).hasSize(2).allMatch(m -> m.startsWith(NsqTopic.ORDER_CREATED + " "));
            verify(cartRepository).deleteCart(userId);
        }

        @Test
        void publishFailureDoesNotFailCheckout() {
            publisher.error = new RuntimeException("nsq down");
            when(cartRepository.getCart(userId)).thenReturn(cart());
            reserveAll();

            assertThat(orderService.checkout(userId, ADDRESS)).hasSize(2);
            assertThat(publisher.messages).isEmpty();
        }

        @Test
        void insufficientStock() {
            when(cartRepository.getCart(userId)).thenReturn(cart());
            when(orderRepository.createOrdersWithStock(any(), any()))
                    .thenThrow(new StockUnavailableException(productA1, "Laptop"));

            assertThatThrownBy(() -> orderService.checkout(userId, ADDRESS))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessage("insufficient stock for product Laptop");
            verify(cartRepository, never()).deleteCart(any());
        }

        @Test
        void productNoLongerExists() {
            when(cartRepository.getCart(userId)).thenReturn(cart());
            when(orderRepository.createOrdersWithStock(any(), any())).thenThrow(new ProductNotFoundException(productB1));

            assertThatThrownBy(() -> orderService.checkout(userId, ADDRESS))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("product " + productB1 + " not found");
        }

        @Test
        void databaseFailure() {
            when(cartRepository.getCart(userId)).thenReturn(cart());
            when(orderRepository.createOrdersWithStock(any(), any())).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> orderService.checkout(userId, ADDRESS))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to process checkout");
        }
    }

    @Test
    void buildOrdersByStore() {
        UUID storeA = UUID.randomUUID();
        UUID storeB = UUID.randomUUID();
        List<CartItem> items = List.of(
                CartItem.builder().productId(UUID.randomUUID()).quantity(2).build(),
                CartItem.builder().productId(UUID.randomUUID()).quantity(1).build(),
                CartItem.builder().productId(UUID.randomUUID()).quantity(3).build());
        List<Product> reserved = List.of(
                Product.builder().id(items.get(0).getProductId()).storeId(storeA).price(new BigDecimal("100")).build(),
                Product.builder().id(items.get(1).getProductId()).storeId(storeB).price(new BigDecimal("50")).build(),
                Product.builder().id(items.get(2).getProductId()).storeId(storeA).price(new BigDecimal("10")).build());

        List<Order> orders = OrderService.buildOrdersByStore(userId, "addr", items, reserved);

        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getStoreId()).isEqualTo(storeA);
        assertThat(orders.get(0).getOrderItems()).hasSize(2);
        assertThat(orders.get(0).getTotalAmount()).isEqualByComparingTo("230");
        assertThat(orders.get(1).getStoreId()).isEqualTo(storeB);
        assertThat(orders.get(1).getTotalAmount()).isEqualByComparingTo("50");
        for (Order order : orders) {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getUserId()).isEqualTo(userId);
            assertThat(order.getPayment().getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(order.getPayment().getAmount()).isEqualByComparingTo(order.getTotalAmount());
        }
    }

    @Test
    void retryPendingPayments() {
        List<Order> stale = List.of(
                Order.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).totalAmount(new BigDecimal("10")).build(),
                Order.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).totalAmount(new BigDecimal("20.50")).build());
        when(orderRepository.findStalePending(any(), eq(50))).thenReturn(stale);

        int count = orderService.retryPendingPayments(Duration.ofMinutes(2), 50);

        assertThat(count).isEqualTo(2);
        assertThat(publisher.messages).hasSize(2);
        assertThat(publisher.messages.getFirst()).isEqualTo(NsqTopic.ORDER_CREATED + " {\"order_id\":\""
                + stale.getFirst().getId() + "\",\"total_amount\":\"10\",\"user_id\":\"" + stale.getFirst().getUserId() + "\"}");
        assertThat(publisher.messages.get(1)).contains("\"total_amount\":\"20.5\"");
    }

    @Nested
    class GetOrders {

        @Test
        void success() {
            when(orderRepository.findByUserId(userId, 1, 10)).thenReturn(new PageResult<>(List.of(
                    Order.builder().id(UUID.randomUUID()).userId(userId).status(OrderStatus.PENDING)
                            .totalAmount(new BigDecimal("50000")).build(),
                    Order.builder().id(UUID.randomUUID()).userId(userId).status(OrderStatus.PAID)
                            .totalAmount(new BigDecimal("100000")).build()), 2));

            PageResult<OrderResponse> page = orderService.getOrders(userId, 1, 10);

            assertThat(page.items()).hasSize(2);
            assertThat(page.total()).isEqualTo(2);
        }

        @Test
        void defaultPaginationOnZeroValues() {
            when(orderRepository.findByUserId(userId, 1, 10)).thenReturn(new PageResult<>(List.of(), 0));

            assertThat(orderService.getOrders(userId, 0, 0).items()).isEmpty();
        }

        @Test
        void dbError() {
            when(orderRepository.findByUserId(userId, 1, 10)).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> orderService.getOrders(userId, 1, 10))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to fetch orders");
        }
    }

    @Nested
    class GetOrderById {

        private final UUID orderId = UUID.randomUUID();

        @Test
        void success() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(
                    Order.builder().id(orderId).userId(userId).status(OrderStatus.PENDING).build()));

            OrderResponse resp = orderService.getOrderById(userId, orderId);

            assertThat(resp.id()).isEqualTo(orderId);
            assertThat(resp.items()).as("an order without items reports null items, like Go").isNull();
        }

        @Test
        void orderNotFound() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(userId, orderId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("order not found");
        }

        @Test
        void forbiddenForAnotherUser() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(
                    Order.builder().id(orderId).userId(UUID.randomUUID()).build()));

            assertThatThrownBy(() -> orderService.getOrderById(userId, orderId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("forbidden");
        }
    }

    @Nested
    class CancelOrder {

        private final UUID orderId = UUID.randomUUID();

        private void orderWithStatus(OrderStatus status) {
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(
                    Order.builder().id(orderId).userId(userId).status(status).build()));
        }

        @Test
        void success() {
            orderWithStatus(OrderStatus.PAID);
            when(orderRepository.cancelAndRestock(orderId, OrderStatus.PAID)).thenReturn(true);

            assertThatCode(() -> orderService.cancelOrder(userId, orderId)).doesNotThrowAnyException();
        }

        @Test
        void statusChangedConcurrently() {
            orderWithStatus(OrderStatus.PROCESSING);
            when(orderRepository.cancelAndRestock(orderId, OrderStatus.PROCESSING)).thenReturn(false);

            assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessage("cannot cancel order, status changed");
        }

        @Test
        void orderNotFound() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("order not found");
        }

        @Test
        void forbiddenForAnotherUser() {
            orderWithStatus(OrderStatus.PENDING);

            assertThatThrownBy(() -> orderService.cancelOrder(UUID.randomUUID(), orderId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("forbidden");
        }

        @Test
        void cannotCancelAShippedOrder() {
            orderWithStatus(OrderStatus.SHIPPED);

            assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessage("cannot cancel order with status shipped");
            verify(orderRepository, never()).cancelAndRestock(any(), any());
        }

        @Test
        void databaseFailure() {
            orderWithStatus(OrderStatus.PENDING);
            when(orderRepository.cancelAndRestock(orderId, OrderStatus.PENDING)).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to cancel order");
        }
    }

    @Nested
    class UpdateOrderStatus {

        private final UUID orderId = UUID.randomUUID();
        private final UUID sellerId = UUID.randomUUID();
        private final UUID storeId = UUID.randomUUID();

        private void order(OrderStatus status, UUID orderStoreId) {
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(
                    Order.builder().id(orderId).storeId(orderStoreId).status(status).build()));
        }

        private void sellerOwnsStore() {
            when(storeRepository.findByUserId(sellerId)).thenReturn(Optional.of(Store.builder().id(storeId).build()));
        }

        @Test
        void paidToProcessing() {
            order(OrderStatus.PAID, storeId);
            sellerOwnsStore();
            when(orderRepository.updateStatusIfCurrent(orderId, OrderStatus.PAID, OrderStatus.PROCESSING)).thenReturn(true);

            assertThatCode(() -> orderService.updateOrderStatus(sellerId, orderId, OrderStatus.PROCESSING.value()))
                    .doesNotThrowAnyException();
        }

        @Test
        void processingToShipping() {
            order(OrderStatus.PROCESSING, storeId);
            sellerOwnsStore();
            when(orderRepository.updateStatusIfCurrent(orderId, OrderStatus.PROCESSING, OrderStatus.SHIPPING))
                    .thenReturn(true);

            assertThatCode(() -> orderService.updateOrderStatus(sellerId, orderId, OrderStatus.SHIPPING.value()))
                    .doesNotThrowAnyException();
        }

        @Test
        void statusChangedConcurrently() {
            order(OrderStatus.PROCESSING, storeId);
            sellerOwnsStore();
            when(orderRepository.updateStatusIfCurrent(orderId, OrderStatus.PROCESSING, OrderStatus.SHIPPING))
                    .thenReturn(false);

            assertThatThrownBy(() -> orderService.updateOrderStatus(sellerId, orderId, OrderStatus.SHIPPING.value()))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessage("cannot update order, status changed");
        }

        @Test
        void orderNotFound() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updateOrderStatus(sellerId, orderId, OrderStatus.PROCESSING.value()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("order not found");
        }

        @Test
        void pendingHasNoValidTransition() {
            order(OrderStatus.PENDING, storeId);

            assertThatThrownBy(() -> orderService.updateOrderStatus(sellerId, orderId, OrderStatus.PROCESSING.value()))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessage("cannot transition from status pending: invalid status transition");
        }

        @Test
        void paidCannotSkipToShipped() {
            order(OrderStatus.PAID, storeId);

            assertThatThrownBy(() -> orderService.updateOrderStatus(sellerId, orderId, OrderStatus.SHIPPED.value()))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessage("invalid status transition from paid to shipped");
        }

        /** The requested status is user input; a status like "failed" must not turn into a 500. */
        @Test
        void requestedStatusTextDoesNotChangeTheErrorKind() {
            order(OrderStatus.PAID, storeId);

            assertThatThrownBy(() -> orderService.updateOrderStatus(sellerId, orderId, "failed"))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessage("invalid status transition from paid to failed");
        }

        @Test
        void storeNotFound() {
            order(OrderStatus.PAID, storeId);
            when(storeRepository.findByUserId(sellerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updateOrderStatus(sellerId, orderId, OrderStatus.PROCESSING.value()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("store not found");
        }

        @Test
        void forbiddenWhenTheOrderIsFromAnotherStore() {
            order(OrderStatus.PAID, UUID.randomUUID());
            sellerOwnsStore();

            assertThatThrownBy(() -> orderService.updateOrderStatus(sellerId, orderId, OrderStatus.PROCESSING.value()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("forbidden: order does not belong to your store");
        }
    }

    @Nested
    class GetSellerOrders {

        private final UUID storeId = UUID.randomUUID();

        @Test
        void success() {
            when(storeRepository.findByUserId(userId)).thenReturn(Optional.of(Store.builder().id(storeId).userId(userId).build()));
            when(orderRepository.findByStoreId(storeId, 1, 10)).thenReturn(new PageResult<>(List.of(
                    Order.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).status(OrderStatus.PAID).build()), 1));

            assertThat(orderService.getSellerOrders(userId, 1, 10).items()).hasSize(1);
        }

        @Test
        void storeNotFound() {
            when(storeRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getSellerOrders(userId, 1, 10))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("store not found");
        }

        @Test
        void dbErrorOnOrders() {
            when(storeRepository.findByUserId(userId)).thenReturn(Optional.of(Store.builder().id(storeId).userId(userId).build()));
            when(orderRepository.findByStoreId(eq(storeId), any(Long.class), anyInt())).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> orderService.getSellerOrders(userId, 1, 10))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to fetch orders");
        }
    }

    @Nested
    class ProcessPaymentResult {

        private final UUID orderId = UUID.randomUUID();

        @Test
        void paymentSuccessMarksTheOrderPaid() {
            when(orderRepository.markPaymentSucceeded(orderId)).thenReturn(true);

            assertThatCode(() -> orderService.processPaymentResult(orderId, true)).doesNotThrowAnyException();
        }

        @Test
        void paymentSuccessForANonPendingOrderIsIgnored() {
            when(orderRepository.markPaymentSucceeded(orderId)).thenReturn(false);

            assertThatCode(() -> orderService.processPaymentResult(orderId, true)).doesNotThrowAnyException();
        }

        @Test
        void paymentSuccessDatabaseErrorIsThrownForRequeue() {
            when(orderRepository.markPaymentSucceeded(orderId)).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> orderService.processPaymentResult(orderId, true)).isNotNull();
        }

        @Test
        void paymentFailedCancelsAndRestocks() {
            when(orderRepository.markPaymentFailed(orderId)).thenReturn(true);

            assertThatCode(() -> orderService.processPaymentResult(orderId, false)).doesNotThrowAnyException();
        }

        @Test
        void paymentFailedForANonPendingOrderIsIgnored() {
            when(orderRepository.markPaymentFailed(orderId)).thenReturn(false);

            assertThatCode(() -> orderService.processPaymentResult(orderId, false)).doesNotThrowAnyException();
        }

        @Test
        void paymentFailedDatabaseErrorIsThrownForRequeue() {
            when(orderRepository.markPaymentFailed(orderId)).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> orderService.processPaymentResult(orderId, false)).isNotNull();
        }
    }
}
