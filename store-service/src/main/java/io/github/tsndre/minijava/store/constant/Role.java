package io.github.tsndre.minijava.store.constant;

import com.fasterxml.jackson.annotation.JsonValue;

/** User roles; every new account starts as a buyer and becomes a seller by opening a store. */
public enum Role {

    ADMIN("admin"),
    BUYER("buyer"),
    SELLER("seller");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static Role fromValue(String value) {
        for (Role role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("unknown role: " + value);
    }
}
