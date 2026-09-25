package io.github.tsndre.minijava.store.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID userId,
        String userName,
        UUID productId,
        int rating,
        String comment,
        OffsetDateTime createdAt) {
}
