package io.github.tsndre.minijava.store.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

    /** Go's zero time, which a cart that was never saved reports as its updated_at. */
    public static final OffsetDateTime ZERO_TIME = OffsetDateTime.of(1, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private UUID userId;
    private List<CartItem> items = new ArrayList<>();
    private OffsetDateTime updatedAt = ZERO_TIME;

    public Cart(UUID userId) {
        this.userId = userId;
    }
}
