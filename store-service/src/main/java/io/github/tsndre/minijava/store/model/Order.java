package io.github.tsndre.minijava.store.model;

import io.github.tsndre.minijava.store.constant.OrderStatus;
import io.github.tsndre.minijava.store.dto.response.OrderItemResponse;
import io.github.tsndre.minijava.store.dto.response.OrderResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private UUID id;
    private UUID userId;
    private UUID storeId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String shippingAddress;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** Stored in their own tables; the repository loads and inserts them. */
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();
    private Payment payment;

    public OrderResponse toResponse() {
        List<OrderItemResponse> items = null;
        for (OrderItem item : orderItems) {
            if (items == null) {
                items = new ArrayList<>();
            }
            items.add(new OrderItemResponse(item.getId(), item.getProductId(), item.getQuantity(), item.getPrice(),
                    item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))));
        }
        return new OrderResponse(id, userId, storeId, status, totalAmount, shippingAddress, items,
                payment == null ? null : payment.toResponse(), createdAt, updatedAt);
    }
}
