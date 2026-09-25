package io.github.tsndre.minijava.store.constant;

public enum ErrorCode {

    VALIDATION("VALIDATION_ERROR"),
    NOT_FOUND("NOT_FOUND"),
    UNAUTHORIZED("UNAUTHORIZED"),
    FORBIDDEN("FORBIDDEN"),
    CONFLICT("CONFLICT"),
    INTERNAL("INTERNAL_ERROR"),
    RATE_LIMITED("RATE_LIMITED"),
    INSUFFICIENT_STOCK("INSUFFICIENT_STOCK"),
    INVALID_STATUS("INVALID_STATUS"),
    TIMEOUT("REQUEST_TIMEOUT");

    private final String value;

    ErrorCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
