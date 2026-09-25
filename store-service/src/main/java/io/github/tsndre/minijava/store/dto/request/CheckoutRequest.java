package io.github.tsndre.minijava.store.dto.request;

/** Missing fields read as empty strings and zeros, like Go's zero values. */
public record CheckoutRequest(
        String shippingAddress) {

    public CheckoutRequest {
        shippingAddress = shippingAddress == null ? "" : shippingAddress;
    }
}
