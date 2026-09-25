package io.github.tsndre.minijava.store.model;

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
public class OrderItem {

    private UUID id;
    private UUID orderId;
    private UUID productId;
    private int quantity;
    private BigDecimal price;
    private OffsetDateTime createdAt;
}
