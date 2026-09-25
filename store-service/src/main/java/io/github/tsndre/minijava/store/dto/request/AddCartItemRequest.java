package io.github.tsndre.minijava.store.dto.request;

/** Missing fields read as empty strings and zeros, like Go's zero values. */
public record AddCartItemRequest(
        String productId,
        Long quantity) {

    public AddCartItemRequest {
        productId = productId == null ? "" : productId;
        quantity = quantity == null ? 0L : quantity;
    }
}
