package io.github.tsndre.minijava.payment.constant;

public enum PaymentMethod {

    MOCK("mock");

    private final String value;

    PaymentMethod(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
