package io.github.tsndre.minijava.store.constant;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentStatus {

    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed"),
    /** A payment that was still pending when the buyer cancelled the order. */
    CANCELLED("cancelled");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static PaymentStatus fromValue(String value) {
        for (PaymentStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown payment status: " + value);
    }
}
