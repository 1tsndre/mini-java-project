package io.github.tsndre.minijava.store.dto.response;

import io.github.tsndre.minijava.store.constant.PaymentMethod;
import io.github.tsndre.minijava.store.constant.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** paid_at is always present, null until the payment succeeds. */
public record PaymentResponse(
        UUID id,
        UUID orderId,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal amount,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt) {
}
