package io.github.tsndre.minijava.store.constant;

import java.time.Duration;

public final class OutboxRelay {

    /** How often the outbox relay publishes queued messages. */
    public static final Duration INTERVAL = Duration.ofSeconds(1);
    /** Caps how many messages the relay publishes per run. */
    public static final int BATCH_SIZE = 100;

    private OutboxRelay() {
    }
}
