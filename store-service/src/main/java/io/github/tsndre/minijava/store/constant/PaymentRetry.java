package io.github.tsndre.minijava.store.constant;

import java.time.Duration;

public final class PaymentRetry {

    /** How often pending orders are checked for a missing payment result. */
    public static final Duration INTERVAL = Duration.ofMinutes(1);
    /** How long an order may stay pending before order.created is republished. */
    public static final Duration AFTER = Duration.ofMinutes(2);
    /** Caps how many orders are republished per check. */
    public static final int BATCH_SIZE = 100;

    private PaymentRetry() {
    }
}
