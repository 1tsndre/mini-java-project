package io.github.tsndre.minijava.payment.service;

import io.github.tsndre.minijava.payment.constant.PaymentMessage;
import io.github.tsndre.minijava.payment.constant.PaymentStatus;

public record PaymentRecord(String orderId, String paymentId, PaymentStatus status, String amount, String method) {

    PaymentResult toResult() {
        boolean success = status == PaymentStatus.SUCCESS;
        return new PaymentResult(orderId, success, paymentId,
                success ? PaymentMessage.SUCCESS : PaymentMessage.DECLINED);
    }
}
