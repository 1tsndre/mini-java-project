package io.github.tsndre.minijava.store.model;

import io.github.tsndre.minijava.store.constant.PaymentMethod;
import io.github.tsndre.minijava.store.constant.PaymentStatus;
import io.github.tsndre.minijava.store.dto.response.PaymentResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    private UUID id;
    private UUID orderId;
    private PaymentMethod method;
    private PaymentStatus status;
    private BigDecimal amount;
    private OffsetDateTime paidAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public PaymentResponse toResponse() {
        return new PaymentResponse(id, orderId, method, status, amount, paidAt, createdAt);
    }
}
