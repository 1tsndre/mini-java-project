package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.response.ApiError;
import io.github.tsndre.minijava.common.response.ApiResponse;
import io.github.tsndre.minijava.common.response.Meta;
import io.github.tsndre.minijava.common.response.Responses;
import io.github.tsndre.minijava.common.upload.Uploader;
import io.github.tsndre.minijava.store.constant.RateLimitKey;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.request.CreateProductRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateProductRequest;
import io.github.tsndre.minijava.store.model.ProductFilter;
import io.github.tsndre.minijava.store.pagination.Pages;
import io.github.tsndre.minijava.store.service.ProductService;
import io.github.tsndre.minijava.store.util.Uuids;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.tsndre.minijava.store.controller.Validation.MAX_VARCHAR_MESSAGE;
import static io.github.tsndre.minijava.store.controller.Validation.REQUIRED;
import static io.github.tsndre.minijava.store.controller.Validation.exceedsVarchar;
import static io.github.tsndre.minijava.store.controller.Validation.field;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private static final String INVALID_ID = "invalid product id";
    private static final String IMAGE_DIR = "products";

    private final ProductService productService;
    private final Uploader uploader;

    @PostMapping
    @Authenticated
    @RequireRole(Role.SELLER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> createProduct(Meta meta, @CurrentUserId UUID userId,
                                                     @JsonBody CreateProductRequest req) {
        List<ApiError> errors = new ArrayList<>();
        if (req.name().isEmpty()) {
            errors.add(field("name", REQUIRED));
        } else if (exceedsVarchar(req.name())) {
            errors.add(field("name", MAX_VARCHAR_MESSAGE));
        }
        if (req.price().isEmpty()) {
            errors.add(field("price", REQUIRED));
        }
        if (req.categoryId().isEmpty()) {
            errors.add(field("category_id", REQUIRED));
        }
        Validation.check(errors);

        return Responses.success(HttpStatus.CREATED, productService.createProduct(userId, req), meta);
    }

    @GetMapping
    @RateLimited(RateLimitKey.PUBLIC)
    public ResponseEntity<ApiResponse> getProducts(Meta meta, HttpServletRequest request) {
        long page = Pages.parse(request.getParameter("page"));
        long perPage = Pages.parse(request.getParameter("per_page"));

        // The IDs are compared against UUID columns, where PostgreSQL rejects anything that is
        // not a UUID; pass them on in canonical form or reject them as a 400.
        List<ApiError> errors = new ArrayList<>();
        String categoryId = canonicalId("category_id", param(request, "category_id"), errors);
        String storeId = canonicalId("store_id", param(request, "store_id"), errors);
        Validation.check(errors);

        ProductFilter filter = new ProductFilter(
                categoryId,
                storeId,
                param(request, "search"),
                param(request, "min_price"),
                param(request, "max_price"),
                param(request, "sort_by"),
                param(request, "sort_order"),
                page,
                perPage);

        return Paging.respond(productService.getProducts(filter), page, perPage, meta);
    }

    @GetMapping("/{id}")
    @RateLimited(RateLimitKey.PUBLIC)
    public ResponseEntity<ApiResponse> getProduct(Meta meta, @PathUuid(value = "id", message = INVALID_ID) UUID id) {
        return Responses.success(HttpStatus.OK, productService.getProductById(id), meta);
    }

    @PutMapping("/{id}")
    @Authenticated
    @RequireRole(Role.SELLER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> updateProduct(Meta meta, @CurrentUserId UUID userId,
                                                     @PathUuid(value = "id", message = INVALID_ID) UUID id,
                                                     @JsonBody UpdateProductRequest req) {
        if (exceedsVarchar(req.name())) {
            Validation.reject("name", MAX_VARCHAR_MESSAGE);
        }

        return Responses.success(HttpStatus.OK, productService.updateProduct(userId, id, req), meta);
    }

    @DeleteMapping("/{id}")
    @Authenticated
    @RequireRole(Role.SELLER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> deleteProduct(Meta meta, @CurrentUserId UUID userId,
                                                     @PathUuid(value = "id", message = INVALID_ID) UUID id) {
        productService.deleteProduct(userId, id);
        return Responses.success(HttpStatus.OK, Map.of("message", "product deleted"), meta);
    }

    @PostMapping("/{id}/image")
    @Authenticated
    @RequireRole(Role.SELLER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> uploadImage(Meta meta, @CurrentUserId UUID userId,
                                                   @PathUuid(value = "id", message = INVALID_ID) UUID id,
                                                   HttpServletRequest request) {
        return Responses.success(HttpStatus.OK,
                Uploads.store(request, uploader, "image", IMAGE_DIR, path -> productService.updateImage(userId, id, path)),
                meta);
    }

    private static String param(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value;
    }

    /** The ID in canonical form, "" when absent; an invalid ID is added to errors. */
    private static String canonicalId(String field, String value, List<ApiError> errors) {
        if (value.isEmpty()) {
            return "";
        }
        return Uuids.parse(value).map(UUID::toString).orElseGet(() -> {
            errors.add(field(field, "must be a valid UUID"));
            return "";
        });
    }
}
