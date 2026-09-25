package io.github.tsndre.minijava.store.model;

import io.github.tsndre.minijava.store.dto.response.ProductResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private UUID id;
    private UUID storeId;
    private UUID categoryId;
    private String name;
    @Builder.Default
    private String description = "";
    private BigDecimal price;
    private int stock;
    @Builder.Default
    private String imageUrl = "";
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public ProductResponse toResponse() {
        return new ProductResponse(id, storeId, categoryId, name, description, price, stock, imageUrl,
                createdAt, updatedAt);
    }
}
