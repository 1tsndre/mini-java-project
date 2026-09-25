package io.github.tsndre.minijava.store.model;

import io.github.tsndre.minijava.store.dto.response.StoreResponse;
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
public class Store {

    private UUID id;
    private UUID userId;
    private String name;
    @Builder.Default
    private String description = "";
    @Builder.Default
    private String logoUrl = "";
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public StoreResponse toResponse() {
        return new StoreResponse(id, userId, name, description, logoUrl, createdAt, updatedAt);
    }
}
