package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.response.ApiResponse;
import io.github.tsndre.minijava.common.response.Meta;
import io.github.tsndre.minijava.common.response.Responses;
import io.github.tsndre.minijava.store.constant.RateLimitKey;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.request.CreateCategoryRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateCategoryRequest;
import io.github.tsndre.minijava.store.service.CategoryService;
import io.github.tsndre.minijava.store.web.Authenticated;
import io.github.tsndre.minijava.store.web.RateLimited;
import io.github.tsndre.minijava.store.web.RequireRole;
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

import java.util.Map;
import java.util.UUID;

import static io.github.tsndre.minijava.store.controller.Validation.MAX_VARCHAR_MESSAGE;
import static io.github.tsndre.minijava.store.controller.Validation.REQUIRED;
import static io.github.tsndre.minijava.store.controller.Validation.exceedsVarchar;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private static final String INVALID_ID = "invalid category id";

    private final CategoryService categoryService;

    @PostMapping
    @Authenticated
    @RequireRole(Role.ADMIN)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> createCategory(Meta meta, @JsonBody CreateCategoryRequest req) {
        if (req.name().isEmpty()) {
            Validation.reject("name", REQUIRED);
        }
        if (exceedsVarchar(req.name())) {
            Validation.reject("name", MAX_VARCHAR_MESSAGE);
        }

        return Responses.success(HttpStatus.CREATED, categoryService.createCategory(req), meta);
    }

    @GetMapping
    @RateLimited(RateLimitKey.PUBLIC)
    public ResponseEntity<ApiResponse> getCategories(Meta meta) {
        return Responses.success(HttpStatus.OK, categoryService.getAllCategories(), meta);
    }

    @PutMapping("/{id}")
    @Authenticated
    @RequireRole(Role.ADMIN)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> updateCategory(Meta meta, @PathUuid(value = "id", message = INVALID_ID) UUID id,
                                                      @JsonBody UpdateCategoryRequest req) {
        if (exceedsVarchar(req.name())) {
            Validation.reject("name", MAX_VARCHAR_MESSAGE);
        }

        return Responses.success(HttpStatus.OK, categoryService.updateCategory(id, req), meta);
    }

    @DeleteMapping("/{id}")
    @Authenticated
    @RequireRole(Role.ADMIN)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> deleteCategory(Meta meta, @PathUuid(value = "id", message = INVALID_ID) UUID id) {
        categoryService.deleteCategory(id);
        return Responses.success(HttpStatus.OK, Map.of("message", "category deleted"), meta);
    }
}
