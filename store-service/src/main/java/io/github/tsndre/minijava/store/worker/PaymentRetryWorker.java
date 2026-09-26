package io.github.tsndre.minijava.store.worker;

import io.github.tsndre.minijava.store.config.GoDuration;
import io.github.tsndre.minijava.store.constant.PaymentRetry;
import io.github.tsndre.minijava.store.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically republishes order.created for orders that are still pending, so an order whose
 * original publish failed (or whose message was lost) still reaches the payment service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRetryWorker implements SmartLifecycle {

    /** Stops after the HTTP server and before the payment result consumer. */
    private static final int PHASE = DEFAULT_PHASE - 3072;

    private final OrderService orderService;

    private ScheduledExecutorService scheduler;

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("payment-retry").factory());
        long interval = PaymentRetry.INTERVAL.toMillis();
        scheduler.scheduleAtFixedRate(this::runOnce, interval, interval, TimeUnit.MILLISECONDS);
        log.atInfo()
                .addKeyValue("interval", GoDuration.format(PaymentRetry.INTERVAL))
                .addKeyValue("older_than", GoDuration.format(PaymentRetry.AFTER))
                .log("payment retry worker started");
    }

    void runOnce() {
        try {
            int count = orderService.retryPendingPayments(PaymentRetry.AFTER, PaymentRetry.BATCH_SIZE);
            if (count > 0) {
                log.atInfo().addKeyValue("count", count).log("republished pending orders");
            }
        } catch (RuntimeException e) {
            log.error("failed to retry pending payments", e);
        }
    }

    @Override
    public void stop() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(PaymentRetry.INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        scheduler = null;
        log.info("payment retry worker stopped");
    }

    @Override
    public boolean isRunning() {
        return scheduler != null;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
