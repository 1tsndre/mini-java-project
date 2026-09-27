package io.github.tsndre.minijava.payment.messaging;

import com.sproutsocial.nsq.BackoffHandler;
import com.sproutsocial.nsq.Message;
import com.sproutsocial.nsq.Publisher;
import com.sproutsocial.nsq.Subscriber;
import io.github.tsndre.minijava.common.messaging.NsqPublishers;
import io.github.tsndre.minijava.payment.config.AppConfig;
import io.github.tsndre.minijava.payment.constant.NsqTopic;
import io.github.tsndre.minijava.payment.constant.PaymentMethod;
import io.github.tsndre.minijava.payment.service.PaymentResult;
import io.github.tsndre.minijava.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pays for every order.created message and publishes the outcome to payment.success or
 * payment.failed. A result that cannot be published is retried through a requeue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConsumer implements SmartLifecycle {

    /** go-nsq's defaults: lookupd is polled every minute and a message is tried five times. */
    private static final int LOOKUP_INTERVAL_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(30);
    /** Stops first, so in-flight results can still be published before the producer and server stop. */
    private static final int PHASE = DEFAULT_PHASE - 1024;

    private final PaymentService paymentService;
    private final AppConfig config;
    private final JsonMapper jsonMapper;

    private Publisher publisher;
    private Subscriber subscriber;

    record OrderCreated(String orderId, String userId, String totalAmount) {
    }

    @Override
    public void start() {
        publisher = NsqPublishers.create(config.nsqdAddr());
        Subscriber sub = new Subscriber(LOOKUP_INTERVAL_SECONDS, config.lookupdAddr());
        sub.setMaxAttempts(MAX_ATTEMPTS);
        sub.subscribe(NsqTopic.ORDER_CREATED, NsqTopic.CHANNEL_PAYMENT_SERVICE, new BackoffHandler(this::handleOrderCreated));
        subscriber = sub;
        log.info("NSQ order consumer started");
    }

    /** Throws when the result could not be published, so the message is retried. */
    void handleOrderCreated(Message message) {
        OrderCreated payload;
        try {
            // Like Go's json.Unmarshal, the whole message must be a single JSON value.
            payload = jsonMapper.readerFor(OrderCreated.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(message.getData());
        } catch (JacksonException e) {
            log.error("failed to unmarshal order.created, skipping", e);
            return;
        }
        String orderId = payload == null || payload.orderId() == null ? "" : payload.orderId();
        String amount = payload == null || payload.totalAmount() == null ? "" : payload.totalAmount();

        log.atInfo().addKeyValue("order_id", orderId).addKeyValue("amount", amount).log("processing payment");
        PaymentResult result = paymentService.processPayment(orderId, amount, PaymentMethod.MOCK.value());

        // Sorted keys, like Go's encoding of a map.
        Map<String, String> response = new TreeMap<>(Map.of(
                "order_id", result.orderId(),
                "payment_id", result.paymentId(),
                "message", result.message()));
        byte[] body = jsonMapper.writeValueAsBytes(response);

        publisher.publish(result.success() ? NsqTopic.PAYMENT_SUCCESS : NsqTopic.PAYMENT_FAILED, body);
    }

    /** Stops consuming and waits for in-flight messages, so their results are still published. */
    @Override
    public void stop() {
        if (subscriber != null) {
            subscriber.drainInFlight();
            try {
                subscriber.awaitNoMessagesInFlight(DRAIN_TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            subscriber.stop();
            subscriber = null;
            log.info("NSQ order consumer stopped");
        }
        if (publisher != null) {
            publisher.stop();
            publisher = null;
        }
    }

    @Override
    public boolean isRunning() {
        return subscriber != null;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
