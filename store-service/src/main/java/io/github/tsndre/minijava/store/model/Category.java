package io.github.tsndre.minijava.store.model;

import io.github.tsndre.minijava.store.dto.response.CategoryResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    private UUID id;
    private String name;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public CategoryResponse toResponse() {
        return new CategoryResponse(id, name, createdAt, updatedAt);
    }
}
