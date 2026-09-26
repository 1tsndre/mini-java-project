package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.response.ApiResponse;
import io.github.tsndre.minijava.common.response.Meta;
import io.github.tsndre.minijava.common.response.Responses;
import io.github.tsndre.minijava.common.upload.Uploader;
import io.github.tsndre.minijava.store.constant.RateLimitKey;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.request.CreateStoreRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateStoreRequest;
import io.github.tsndre.minijava.store.service.StoreService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static io.github.tsndre.minijava.store.controller.Validation.MAX_VARCHAR_MESSAGE;
import static io.github.tsndre.minijava.store.controller.Validation.REQUIRED;
import static io.github.tsndre.minijava.store.controller.Validation.exceedsVarchar;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private static final String INVALID_ID = "invalid store id";
    private static final String LOGO_DIR = "stores";

    private final StoreService storeService;
    private final Uploader uploader;

    @PostMapping
    @Authenticated
    @RequireRole(Role.BUYER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> createStore(Meta meta, @CurrentUserId UUID userId,
                                                   @JsonBody CreateStoreRequest req) {
        if (req.name().isEmpty()) {
            Validation.reject("name", REQUIRED);
        }
        if (exceedsVarchar(req.name())) {
            Validation.reject("name", MAX_VARCHAR_MESSAGE);
        }

        return Responses.success(HttpStatus.CREATED, storeService.createStore(userId, req), meta);
    }

    @GetMapping("/{id}")
    @RateLimited(RateLimitKey.PUBLIC)
    public ResponseEntity<ApiResponse> getStore(Meta meta, @PathUuid(value = "id", message = INVALID_ID) UUID id) {
        return Responses.success(HttpStatus.OK, storeService.getStoreById(id), meta);
    }

    @PutMapping("/{id}")
    @Authenticated
    @RequireRole(Role.SELLER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> updateStore(Meta meta, @CurrentUserId UUID userId,
                                                   @PathUuid(value = "id", message = INVALID_ID) UUID id,
                                                   @JsonBody UpdateStoreRequest req) {
        if (exceedsVarchar(req.name())) {
            Validation.reject("name", MAX_VARCHAR_MESSAGE);
        }

        return Responses.success(HttpStatus.OK, storeService.updateStore(userId, id, req), meta);
    }

    @PostMapping("/{id}/logo")
    @Authenticated
    @RequireRole(Role.SELLER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> uploadLogo(Meta meta, @CurrentUserId UUID userId,
                                                  @PathUuid(value = "id", message = INVALID_ID) UUID id,
                                                  HttpServletRequest request) {
        return Responses.success(HttpStatus.OK,
                Uploads.store(request, uploader, "logo", LOGO_DIR, path -> storeService.updateLogo(userId, id, path)),
                meta);
    }
}
