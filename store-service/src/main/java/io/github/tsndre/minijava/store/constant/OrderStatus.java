package io.github.tsndre.minijava.store.constant;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

public enum OrderStatus {

    PENDING("pending"),
    PAID("paid"),
    PROCESSING("processing"),
    SHIPPING("shipping"),
    SHIPPED("shipped"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static OrderStatus fromValue(String value) {
        for (OrderStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown order status: " + value);
    }

    /** Whether a buyer may still cancel an order in this status. */
    public boolean isCancellable() {
        return switch (this) {
            case PENDING, PAID, PROCESSING -> true;
            case SHIPPING, SHIPPED, COMPLETED, CANCELLED -> false;
        };
    }

    /** The statuses a seller may move an order to from this one. */
    public List<OrderStatus> transitions() {
        return switch (this) {
            case PAID -> List.of(PROCESSING);
            case PROCESSING -> List.of(SHIPPING);
            case SHIPPING -> List.of(SHIPPED);
            case SHIPPED -> List.of(COMPLETED);
            case PENDING, COMPLETED, CANCELLED -> List.of();
        };
    }
}
