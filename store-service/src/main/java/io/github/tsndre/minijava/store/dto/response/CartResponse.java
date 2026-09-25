package io.github.tsndre.minijava.store.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        BigDecimal total,
        OffsetDateTime updatedAt) {
}
