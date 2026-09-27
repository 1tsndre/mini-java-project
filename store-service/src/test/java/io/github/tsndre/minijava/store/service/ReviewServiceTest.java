package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.dto.request.CreateReviewRequest;
import io.github.tsndre.minijava.store.dto.response.ReviewResponse;
import io.github.tsndre.minijava.store.model.Review;
import io.github.tsndre.minijava.store.repository.PageResult;
import io.github.tsndre.minijava.store.repository.ReviewRepository;
import io.github.tsndre.minijava.store.service.exception.ConflictException;
import io.github.tsndre.minijava.store.service.exception.ForbiddenException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.ValidationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Mock
    private ReviewRepository reviewRepository;
    @InjectMocks
    private ReviewService reviewService;

    @Nested
    class CreateReview {

        private void purchasedButNotReviewed() {
            when(reviewRepository.hasUserPurchased(userId, productId)).thenReturn(true);
            when(reviewRepository.hasUserReviewed(userId, productId)).thenReturn(false);
        }

        @Test
        void success() {
            purchasedButNotReviewed();
            when(reviewRepository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

            ReviewResponse resp = reviewService.createReview(userId, productId, new CreateReviewRequest(5L, "Great product"));

            assertThat(resp.rating()).isEqualTo(5);
            assertThat(resp.userName()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 6})
        void ratingOutOfRange(long rating) {
            assertThatThrownBy(() -> reviewService.createReview(userId, productId, new CreateReviewRequest(rating, "x")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("rating must be between 1 and 5");
            verifyNoInteractions(reviewRepository);
        }

        @Test
        void userHasNotPurchasedTheProduct() {
            when(reviewRepository.hasUserPurchased(userId, productId)).thenReturn(false);

            assertThatThrownBy(() -> reviewService.createReview(userId, productId, new CreateReviewRequest(4L, "Good")))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("you must purchase this product before reviewing");
        }

        @Test
        void userAlreadyReviewed() {
            when(reviewRepository.hasUserPurchased(userId, productId)).thenReturn(true);
            when(reviewRepository.hasUserReviewed(userId, productId)).thenReturn(true);

            assertThatThrownBy(() -> reviewService.createReview(userId, productId, new CreateReviewRequest(3L, "Okay")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("you have already reviewed this product");
        }

        @Test
        void createFails() {
            purchasedButNotReviewed();
            when(reviewRepository.create(any())).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> reviewService.createReview(userId, productId, new CreateReviewRequest(5L, "Great")))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to create review");
        }

        @Test
        void concurrentDuplicateReview() {
            purchasedButNotReviewed();
            when(reviewRepository.create(any())).thenThrow(TestErrors.duplicateKey());

            assertThatThrownBy(() -> reviewService.createReview(userId, productId, new CreateReviewRequest(5L, "Great")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("you have already reviewed this product");
        }
    }

    @Nested
    class GetProductReviews {

        @Test
        void successIncludesTheReviewersName() {
            when(reviewRepository.findByProductId(productId, 1, 10)).thenReturn(new PageResult<>(List.of(
                    Review.builder().id(UUID.randomUUID()).productId(productId).rating(5).userName("Andi").build(),
                    Review.builder().id(UUID.randomUUID()).productId(productId).rating(3).userName("Budi").build()), 2));

            PageResult<ReviewResponse> page = reviewService.getProductReviews(productId, 1, 10);

            assertThat(page.total()).isEqualTo(2);
            assertThat(page.items()).extracting(ReviewResponse::userName).containsExactly("Andi", "Budi");
        }

        @Test
        void noReviewsGiveAnEmptyList() {
            when(reviewRepository.findByProductId(productId, 1, 10)).thenReturn(new PageResult<>(List.of(), 0));

            assertThat(reviewService.getProductReviews(productId, 1, 10).items()).isNotNull().isEmpty();
        }

        @Test
        void repositoryFails() {
            when(reviewRepository.findByProductId(productId, 1, 10)).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> reviewService.getProductReviews(productId, 1, 10))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to fetch reviews");
        }

        @Test
        void pageIsNormalized() {
            when(reviewRepository.findByProductId(productId, 1, 100)).thenReturn(new PageResult<>(List.of(), 0));

            assertThat(reviewService.getProductReviews(productId, -3, 500).items()).isEmpty();
        }
    }
}
