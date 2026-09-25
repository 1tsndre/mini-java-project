package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.model.Cart;

import java.util.UUID;

/** Carts live in Redis, with the cart_items table as the durable backup. */
public interface CartRepository {

    /** The user's cart; a user without one gets an empty cart, never an error. */
    Cart getCart(UUID userId);

    void saveCart(Cart cart);

    void deleteCart(UUID userId);
}
