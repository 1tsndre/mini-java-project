package io.github.tsndre.minijava.store.messaging;

import com.sproutsocial.nsq.BackoffHandler;
import com.sproutsocial.nsq.Message;
import com.sproutsocial.nsq.Subscriber;
import io.github.tsndre.minijava.store.config.AppConfig;
import io.github.tsndre.minijava.store.constant.NsqTopic;
import io.github.tsndre.minijava.store.service.OrderService;
import io.github.tsndre.minijava.store.util.Uuids;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

/**
 * Applies the payment service's results (payment.success / payment.failed) to orders. A message
 * whose processing fails is requeued with a backoff and retried, like a go-nsq handler error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer implements SmartLifecycle {

    /** go-nsq's defaults: lookupd is polled every minute and a message is tried five times. */
    private static final int LOOKUP_INTERVAL_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    /** Starts before and stops after the HTTP server, which still needs the consumer while draining. */
    static final int PHASE = DEFAULT_PHASE - 4096;

    private final OrderService orderService;
    private final AppConfig config;
    private final JsonMapper jsonMapper;

    private Subscriber subscriber;
    private volatile boolean running;

    record PaymentResult(String orderId) {
    }

    @Override
    public void start() {
        try {
            Subscriber sub = new Subscriber(LOOKUP_INTERVAL_SECONDS, config.nsq().lookupdAddr());
            sub.setMaxAttempts(MAX_ATTEMPTS);
            sub.subscribe(NsqTopic.PAYMENT_SUCCESS, NsqTopic.CHANNEL_STORE_SERVICE,
                    new BackoffHandler(message -> handlePaymentResult(message, true)));
            sub.subscribe(NsqTopic.PAYMENT_FAILED, NsqTopic.CHANNEL_STORE_SERVICE,
                    new BackoffHandler(message -> handlePaymentResult(message, false)));
            subscriber = sub;
            log.info("NSQ payment result consumers started");
        } catch (RuntimeException e) {
            log.atWarn().addKeyValue("error", e.getMessage())
                    .log("failed to start NSQ consumer, payment callbacks won't work");
        }
        running = true;
    }

    /** Stops taking messages and waits for the ones in flight, so none is cut off mid-update. */
    @Override
    public void stop() {
        running = false;
        if (subscriber == null) {
            return;
        }
        subscriber.drainInFlight();
        try {
            subscriber.awaitNoMessagesInFlight(config.app().shutdownTimeout());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        subscriber.stop();
        log.info("NSQ payment result consumers stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    /**
     * Throws when the order could not be updated, so the message is retried; a message that can
     * never be processed is logged and dropped.
     */
    void handlePaymentResult(Message message, boolean success) {
        Optional<UUID> orderId = parseOrderId(message.getData());
        orderId.ifPresent(id -> orderService.processPaymentResult(id, success));
    }

    Optional<UUID> parseOrderId(byte[] body) {
        // Like Go's json.Unmarshal, the whole message must be a single JSON value.
        ObjectReader reader = jsonMapper.readerFor(PaymentResult.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        PaymentResult payload;
        try {
            payload = reader.readValue(body);
        } catch (JacksonException e) {
            log.error("failed to unmarshal payment result, skipping", e);
            return Optional.empty();
        }

        String rawOrderId = payload == null || payload.orderId() == null ? "" : payload.orderId();
        Optional<UUID> orderId = Uuids.parse(rawOrderId);
        if (orderId.isEmpty()) {
            log.atError().addKeyValue("order_id", rawOrderId).log("invalid order_id in payment result, skipping");
        }
        return orderId;
    }
}
