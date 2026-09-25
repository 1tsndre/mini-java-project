package io.github.tsndre.minijava.store.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** The PostgreSQL backup of a cart line (table cart_items). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDb {

    private UUID id;
    private UUID userId;
    private UUID productId;
    private int quantity;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
