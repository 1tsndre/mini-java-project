package io.github.tsndre.minijava.store.repository;

import lombok.Getter;

import java.util.UUID;

@Getter
public class StockUnavailableException extends RuntimeException {

    private final UUID productId;
    private final String productName;

    public StockUnavailableException(UUID productId, String productName) {
        super("insufficient stock for product " + productName);
        this.productId = productId;
        this.productName = productName;
    }
}
