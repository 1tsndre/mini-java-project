package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.dto.request.CreateReviewRequest;
import io.github.tsndre.minijava.store.dto.response.ReviewResponse;
import io.github.tsndre.minijava.store.model.Review;
import io.github.tsndre.minijava.store.pagination.Pages;
import io.github.tsndre.minijava.store.repository.PageResult;
import io.github.tsndre.minijava.store.repository.ReviewRepository;
import io.github.tsndre.minijava.store.repository.UniqueViolationException;
import io.github.tsndre.minijava.store.service.exception.ConflictException;
import io.github.tsndre.minijava.store.service.exception.ForbiddenException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    private final ReviewRepository reviewRepository;

    public ReviewResponse createReview(UUID userId, UUID productId, CreateReviewRequest req) {
        if (req.rating() < MIN_RATING || req.rating() > MAX_RATING) {
            throw new ValidationException("rating must be between 1 and 5");
        }

        boolean purchased;
        try {
            purchased = reviewRepository.hasUserPurchased(userId, productId);
        } catch (RuntimeException e) {
            throw new InternalException("failed to verify purchase");
        }
        if (!purchased) {
            throw new ForbiddenException("you must purchase this product before reviewing");
        }

        boolean reviewed;
        try {
            reviewed = reviewRepository.hasUserReviewed(userId, productId);
        } catch (RuntimeException e) {
            throw new InternalException("failed to check existing review");
        }
        if (reviewed) {
            throw new ConflictException("you have already reviewed this product");
        }

        Review review = Review.builder()
                .userId(userId)
                .productId(productId)
                .rating(req.rating().intValue())
                .comment(req.comment())
                .build();

        try {
            review = reviewRepository.create(review);
        } catch (UniqueViolationException e) {
            // A concurrent review by the same user passed the check above.
            throw new ConflictException("you have already reviewed this product");
        } catch (RuntimeException e) {
            log.error("failed to create review", e);
            throw new InternalException("failed to create review");
        }

        return review.toResponse();
    }

    public PageResult<ReviewResponse> getProductReviews(UUID productId, long page, long perPage) {
        Pages.Page normalized = Pages.normalize(page, perPage);

        PageResult<Review> reviews;
        try {
            reviews = reviewRepository.findByProductId(productId, normalized.page(), normalized.perPage());
        } catch (RuntimeException e) {
            throw new InternalException("failed to fetch reviews");
        }

        return new PageResult<>(reviews.items().stream().map(Review::toResponse).toList(), reviews.total());
    }
}
