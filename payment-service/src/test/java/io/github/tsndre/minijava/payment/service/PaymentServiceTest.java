package io.github.tsndre.minijava.payment.service;

import io.github.tsndre.minijava.payment.constant.PaymentMessage;
import io.github.tsndre.minijava.payment.constant.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceTest {

    private final PaymentService paymentService = new PaymentService();

    @Test
    void processPaymentIsIdempotent() {
        PaymentResult first = paymentService.processPayment("order-1", "100.00", "mock");
        PaymentResult second = paymentService.processPayment("order-1", "100.00", "mock");

        assertThat(second.success()).isEqualTo(first.success());
        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(second.message()).isEqualTo(first.message());
        assertThat(paymentService.getStatus("order-1")).hasValueSatisfying(
                rec -> assertThat(rec.paymentId()).isEqualTo(first.paymentId()));
    }

    @Test
    void concurrentDuplicatesAgree() throws Exception {
        List<Future<PaymentResult>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 5; i++) {
                results.add(executor.submit(() -> paymentService.processPayment("order-2", "50.00", "mock")));
            }
        }

        PaymentRecord recorded = paymentService.getStatus("order-2").orElseThrow();
        for (Future<PaymentResult> result : results) {
            assertThat(result.get().success())
                    .as("every duplicate must report the recorded outcome")
                    .isEqualTo(recorded.status() == PaymentStatus.SUCCESS);
        }
    }

    @Test
    void getStatusOfAnUnknownOrder() {
        assertThat(paymentService.getStatus("missing")).isEmpty();
    }

    @Test
    void paymentIdIsPrefixedWithTheStartOfTheOrderId() {
        PaymentResult result = paymentService.processPayment("0b9f2c4e-1111-2222-3333-444455556666", "10", "mock");

        assertThat(result.paymentId()).isEqualTo("pay_0b9f2c4e");
        assertThat(result.message()).isIn(PaymentMessage.SUCCESS, PaymentMessage.DECLINED);
    }
}
