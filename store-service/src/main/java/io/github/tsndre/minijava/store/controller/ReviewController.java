package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.response.ApiResponse;
import io.github.tsndre.minijava.common.response.Meta;
import io.github.tsndre.minijava.common.response.Responses;
import io.github.tsndre.minijava.store.constant.RateLimitKey;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.request.CreateReviewRequest;
import io.github.tsndre.minijava.store.pagination.Pages;
import io.github.tsndre.minijava.store.service.ReviewService;
import io.github.tsndre.minijava.store.web.Authenticated;
import io.github.tsndre.minijava.store.web.RateLimited;
import io.github.tsndre.minijava.store.web.RequireRole;
import io.github.tsndre.minijava.store.web.bind.CurrentUserId;
import io.github.tsndre.minijava.store.web.bind.JsonBody;
import io.github.tsndre.minijava.store.web.bind.PathUuid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{id}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private static final String INVALID_ID = "invalid product id";

    private final ReviewService reviewService;

    @PostMapping
    @Authenticated
    @RequireRole(Role.BUYER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> createReview(Meta meta, @CurrentUserId UUID userId,
                                                    @PathUuid(value = "id", message = INVALID_ID) UUID productId,
                                                    @JsonBody CreateReviewRequest req) {
        if (req.rating() < 1 || req.rating() > 5) {
            Validation.reject("rating", "must be between 1 and 5");
        }

        return Responses.success(HttpStatus.CREATED, reviewService.createReview(userId, productId, req), meta);
    }

    @GetMapping
    @RateLimited(RateLimitKey.PUBLIC)
    public ResponseEntity<ApiResponse> getProductReviews(Meta meta,
                                                         @PathUuid(value = "id", message = INVALID_ID) UUID productId,
                                                         HttpServletRequest request) {
        long page = Pages.parse(request.getParameter("page"));
        long perPage = Pages.parse(request.getParameter("per_page"));

        return Paging.respond(reviewService.getProductReviews(productId, page, perPage), page, perPage, meta);
    }
}
