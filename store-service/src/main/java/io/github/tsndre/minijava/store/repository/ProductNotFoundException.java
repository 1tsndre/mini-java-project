package io.github.tsndre.minijava.store.repository;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ProductNotFoundException extends RuntimeException {

    private final UUID productId;

    public ProductNotFoundException(UUID productId) {
        super("product " + productId + " not found");
        this.productId = productId;
    }
}
