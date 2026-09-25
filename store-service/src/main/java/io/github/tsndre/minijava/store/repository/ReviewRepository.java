package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.model.Review;

import java.util.UUID;

public interface ReviewRepository {

    Review create(Review review);

    /** A page of a product's reviews, newest first, with each reviewer's name. */
    PageResult<Review> findByProductId(UUID productId, long page, int perPage);

    boolean hasUserReviewed(UUID userId, UUID productId);

    /** Whether the user has a shipped or completed order containing the product. */
    boolean hasUserPurchased(UUID userId, UUID productId);
}
