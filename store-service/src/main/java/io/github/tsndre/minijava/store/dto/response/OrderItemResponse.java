package io.github.tsndre.minijava.store.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        int quantity,
        BigDecimal price,
        BigDecimal subtotal) {
}
