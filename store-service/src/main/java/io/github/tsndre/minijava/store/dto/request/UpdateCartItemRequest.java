package io.github.tsndre.minijava.store.dto.request;

/** Missing fields read as empty strings and zeros, like Go's zero values. */
public record UpdateCartItemRequest(
        Long quantity) {

    public UpdateCartItemRequest {
        quantity = quantity == null ? 0L : quantity;
    }
}
