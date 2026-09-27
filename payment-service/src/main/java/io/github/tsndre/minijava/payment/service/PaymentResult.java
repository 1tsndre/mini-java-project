package io.github.tsndre.minijava.payment.service;

public record PaymentResult(String orderId, boolean success, String paymentId, String message) {
}
