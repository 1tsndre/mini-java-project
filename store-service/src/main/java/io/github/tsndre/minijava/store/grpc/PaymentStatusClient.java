package io.github.tsndre.minijava.store.grpc;

import io.github.tsndre.minijava.store.dto.response.PaymentStatusResponse;

public interface PaymentStatusClient {

    /**
     * @throws RuntimeException if the payment service could not be reached or has no such payment
     */
    PaymentStatusResponse getStatus(String orderId);
}
