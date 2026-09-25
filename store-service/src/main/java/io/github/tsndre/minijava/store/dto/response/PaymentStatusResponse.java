package io.github.tsndre.minijava.store.dto.response;

/** The payment processor's own record, read from payment-service over gRPC. */
public record PaymentStatusResponse(
        String orderId,
        String paymentId,
        String status,
        String amount,
        String method) {
}
