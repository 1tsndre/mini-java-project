package io.github.tsndre.minijava.store.model;

import io.github.tsndre.minijava.store.dto.response.ReviewResponse;
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
public class Review {

    private UUID id;
    private UUID userId;
    private UUID productId;
    private int rating;
    @Builder.Default
    private String comment = "";
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** The reviewer's name, joined from users when reviews are listed. */
    @Builder.Default
    private String userName = "";

    public ReviewResponse toResponse() {
        return new ReviewResponse(id, userId, userName, productId, rating, comment, createdAt);
    }
}
