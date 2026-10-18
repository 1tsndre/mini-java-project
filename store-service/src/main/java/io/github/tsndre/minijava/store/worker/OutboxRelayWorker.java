package io.github.tsndre.minijava.store.worker;

import io.github.tsndre.minijava.store.config.GoDuration;
import io.github.tsndre.minijava.store.constant.OutboxRelay;
import io.github.tsndre.minijava.store.repository.OutboxRepository;
import io.github.tsndre.minijava.store.service.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Publishes the messages queued in the outbox to NSQ. A message is only deleted once nsqd has
 * accepted it, so a crash or an unreachable nsqd delays a message instead of losing it; one may be
 * published twice, which the consumers tolerate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayWorker implements SmartLifecycle {

    /** Stops after the payment retry worker and before the payment result consumer. */
    private static final int PHASE = DEFAULT_PHASE - 3584;

    private final OutboxRepository outboxRepository;
    private final MessagePublisher publisher;

    private ScheduledExecutorService scheduler;

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("outbox-relay").factory());
        long interval = OutboxRelay.INTERVAL.toMillis();
        scheduler.scheduleAtFixedRate(this::runOnce, interval, interval, TimeUnit.MILLISECONDS);
        log.atInfo().addKeyValue("interval", GoDuration.format(OutboxRelay.INTERVAL)).log("outbox relay started");
    }

    void runOnce() {
        try {
            outboxRepository.publishPending(OutboxRelay.BATCH_SIZE,
                            message -> publisher.publish(message.topic(), message.payload().getBytes(StandardCharsets.UTF_8)))
                    .rejection()
                    .ifPresent(e -> log.error("failed to relay outbox messages", e));
        } catch (RuntimeException e) {
            log.error("failed to relay outbox messages", e);
        }
    }

    /** Waits for a run in progress, so its messages are not published after NSQ closes. */
    @Override
    public void stop() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(OutboxRelay.INTERVAL.toMillis() * 10, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        scheduler = null;
        log.info("outbox relay stopped");
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
