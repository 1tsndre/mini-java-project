package io.github.tsndre.minijava.store.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StoreResponse(
        UUID id,
        UUID userId,
        String name,
        String description,
        String logoUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
