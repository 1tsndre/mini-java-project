package io.github.tsndre.minijava.store.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID productId,
        String name,
        BigDecimal price,
        int quantity,
        BigDecimal subtotal,
        String imageUrl) {
}
