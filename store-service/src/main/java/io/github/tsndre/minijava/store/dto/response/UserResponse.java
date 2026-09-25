package io.github.tsndre.minijava.store.dto.response;

import io.github.tsndre.minijava.store.constant.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        Role role,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
