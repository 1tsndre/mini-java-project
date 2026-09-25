package io.github.tsndre.minijava.store.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID storeId,
        UUID categoryId,
        String name,
        String description,
        BigDecimal price,
        int stock,
        String imageUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
