package io.github.tsndre.minijava.payment.service;

import io.github.tsndre.minijava.payment.constant.PaymentMessage;
import io.github.tsndre.minijava.payment.constant.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class PaymentService {

    private static final int MIN_DELAY_MILLIS = 500;
    private static final int DELAY_SPREAD_MILLIS = 1500;
    private static final double SUCCESS_RATE = 0.9;
    private static final String PAYMENT_ID_PREFIX = "pay_";
    private static final int PAYMENT_ID_ORDER_CHARS = 8;

    private final Map<String, PaymentRecord> records = new ConcurrentHashMap<>();

    /**
     * Idempotent per order: a redelivered or republished request for an order that was already
     * processed returns the recorded outcome instead of charging again with a fresh random result.
     */
    public PaymentResult processPayment(String orderId, String amount, String method) {
        Optional<PaymentRecord> existing = getStatus(orderId);
        if (existing.isPresent()) {
            log.atInfo().addKeyValue("order_id", orderId).log("payment already processed, returning recorded result");
            return existing.get().toResult();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        sleep(MIN_DELAY_MILLIS + random.nextInt(DELAY_SPREAD_MILLIS));

        boolean success = random.nextFloat() < SUCCESS_RATE;
        String suffix = orderId.length() > PAYMENT_ID_ORDER_CHARS ? orderId.substring(0, PAYMENT_ID_ORDER_CHARS) : orderId;
        String paymentId = PAYMENT_ID_PREFIX + suffix;

        PaymentStatus status;
        String message;
        if (success) {
            status = PaymentStatus.SUCCESS;
            message = PaymentMessage.SUCCESS;
            log.atInfo().addKeyValue("order_id", orderId).addKeyValue("amount", amount).log(message);
        } else {
            status = PaymentStatus.FAILED;
            message = PaymentMessage.DECLINED;
            log.atWarn().addKeyValue("order_id", orderId).addKeyValue("amount", amount).log(message);
        }

        // A concurrent duplicate may have finished first; keep whichever was recorded first.
        PaymentRecord recorded = records.putIfAbsent(orderId,
                new PaymentRecord(orderId, paymentId, status, amount, method));
        if (recorded != null) {
            return recorded.toResult();
        }
        return new PaymentResult(orderId, success, paymentId, message);
    }

    public Optional<PaymentRecord> getStatus(String orderId) {
        return Optional.ofNullable(records.get(orderId));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
