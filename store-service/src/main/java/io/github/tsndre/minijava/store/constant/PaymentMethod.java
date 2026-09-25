package io.github.tsndre.minijava.store.constant;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentMethod {

    MOCK("mock");

    private final String value;

    PaymentMethod(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static PaymentMethod fromValue(String value) {
        for (PaymentMethod method : values()) {
            if (method.value.equals(value)) {
                return method;
            }
        }
        throw new IllegalArgumentException("unknown payment method: " + value);
    }
}
