package io.github.tsndre.minijava.store.repository;

import java.util.UUID;

public record StockReservation(UUID productId, int quantity) {
}
