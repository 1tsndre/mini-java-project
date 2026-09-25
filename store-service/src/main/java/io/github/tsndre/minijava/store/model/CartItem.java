package io.github.tsndre.minijava.store.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    private UUID productId;
    private String name;
    private BigDecimal price;
    private int quantity;
    private String imageUrl;
}
