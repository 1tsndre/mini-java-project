package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.response.ApiError;
import io.github.tsndre.minijava.common.response.ApiResponse;
import io.github.tsndre.minijava.common.response.Meta;
import io.github.tsndre.minijava.common.response.Responses;
import io.github.tsndre.minijava.store.constant.RateLimitKey;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.request.AddCartItemRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateCartItemRequest;
import io.github.tsndre.minijava.store.service.CartService;
import io.github.tsndre.minijava.store.web.Authenticated;
import io.github.tsndre.minijava.store.web.RateLimited;
import io.github.tsndre.minijava.store.web.RequireRole;
import io.github.tsndre.minijava.store.web.bind.CurrentUserId;
import io.github.tsndre.minijava.store.web.bind.JsonBody;
import io.github.tsndre.minijava.store.web.bind.PathUuid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.tsndre.minijava.store.controller.Validation.REQUIRED;
import static io.github.tsndre.minijava.store.controller.Validation.field;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private static final String INVALID_PRODUCT_ID = "invalid product id";
    private static final String QUANTITY_NOT_POSITIVE = "must be greater than 0";

    private final CartService cartService;

    @GetMapping
    @Authenticated
    @RequireRole(Role.BUYER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> getCart(Meta meta, @CurrentUserId UUID userId) {
        return Responses.success(HttpStatus.OK, cartService.getCart(userId), meta);
    }

    @PostMapping("/items")
    @Authenticated
    @RequireRole(Role.BUYER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> addItem(Meta meta, @CurrentUserId UUID userId,
                                               @JsonBody AddCartItemRequest req) {
        List<ApiError> errors = new ArrayList<>();
        if (req.productId().isEmpty()) {
            errors.add(field("product_id", REQUIRED));
        }
        if (req.quantity() <= 0) {
            errors.add(field("quantity", QUANTITY_NOT_POSITIVE));
        }
        Validation.check(errors);

        return Responses.success(HttpStatus.OK, cartService.addItem(userId, req), meta);
    }

    @PutMapping("/items/{product_id}")
    @Authenticated
    @RequireRole(Role.BUYER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> updateItem(Meta meta, @CurrentUserId UUID userId,
                                                  @PathUuid(value = "product_id", message = INVALID_PRODUCT_ID) UUID productId,
                                                  @JsonBody UpdateCartItemRequest req) {
        if (req.quantity() <= 0) {
            Validation.reject("quantity", QUANTITY_NOT_POSITIVE);
        }

        return Responses.success(HttpStatus.OK, cartService.updateItem(userId, productId, req), meta);
    }

    @DeleteMapping("/items/{product_id}")
    @Authenticated
    @RequireRole(Role.BUYER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> removeItem(Meta meta, @CurrentUserId UUID userId,
                                                  @PathUuid(value = "product_id", message = INVALID_PRODUCT_ID) UUID productId) {
        return Responses.success(HttpStatus.OK, cartService.removeItem(userId, productId), meta);
    }
}
