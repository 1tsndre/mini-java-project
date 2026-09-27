package io.github.tsndre.minijava.payment.constant;

public enum PaymentStatus {

    SUCCESS("success"),
    FAILED("failed"),
    /** Reported for an order the service has no payment for. */
    NOT_FOUND("not_found");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
