package io.github.tsndre.minijava.store.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.tsndre.minijava.store.constant.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        UUID storeId,
        OrderStatus status,
        BigDecimal totalAmount,
        String shippingAddress,
        List<OrderItemResponse> items,
        @JsonInclude(JsonInclude.Include.NON_NULL) PaymentResponse payment,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
