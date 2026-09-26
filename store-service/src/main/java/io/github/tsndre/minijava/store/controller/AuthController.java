package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.response.ApiError;
import io.github.tsndre.minijava.common.response.ApiResponse;
import io.github.tsndre.minijava.common.response.Meta;
import io.github.tsndre.minijava.common.response.Responses;
import io.github.tsndre.minijava.store.constant.RateLimitKey;
import io.github.tsndre.minijava.store.constant.ValidationLimit;
import io.github.tsndre.minijava.store.dto.request.LoginRequest;
import io.github.tsndre.minijava.store.dto.request.RefreshRequest;
import io.github.tsndre.minijava.store.dto.request.RegisterRequest;
import io.github.tsndre.minijava.store.service.AuthService;
import io.github.tsndre.minijava.store.web.RateLimited;
import io.github.tsndre.minijava.store.web.bind.JsonBody;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static io.github.tsndre.minijava.store.controller.Validation.MAX_PASSWORD_MESSAGE;
import static io.github.tsndre.minijava.store.controller.Validation.MAX_VARCHAR_MESSAGE;
import static io.github.tsndre.minijava.store.controller.Validation.REQUIRED;
import static io.github.tsndre.minijava.store.controller.Validation.byteLength;
import static io.github.tsndre.minijava.store.controller.Validation.exceedsVarchar;
import static io.github.tsndre.minijava.store.controller.Validation.field;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Pattern EMAIL = Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    private final AuthService authService;

    @PostMapping("/register")
    @RateLimited({RateLimitKey.LOGIN, RateLimitKey.PUBLIC})
    public ResponseEntity<ApiResponse> register(Meta meta, @JsonBody RegisterRequest req) {
        List<ApiError> errors = new ArrayList<>();
        if (req.email().isEmpty()) {
            errors.add(field("email", REQUIRED));
        } else if (exceedsVarchar(req.email())) {
            errors.add(field("email", MAX_VARCHAR_MESSAGE));
        } else if (!EMAIL.matcher(req.email()).matches()) {
            errors.add(field("email", "invalid email format"));
        }
        if (req.password().isEmpty()) {
            errors.add(field("password", REQUIRED));
        } else if (byteLength(req.password()) < ValidationLimit.MIN_PASSWORD_BYTES) {
            errors.add(field("password", "minimum 6 characters"));
        } else if (byteLength(req.password()) > ValidationLimit.MAX_PASSWORD_BYTES) {
            errors.add(field("password", MAX_PASSWORD_MESSAGE));
        }
        if (req.name().isEmpty()) {
            errors.add(field("name", REQUIRED));
        } else if (exceedsVarchar(req.name())) {
            errors.add(field("name", MAX_VARCHAR_MESSAGE));
        }
        Validation.check(errors);

        return Responses.success(HttpStatus.CREATED, authService.register(req), meta);
    }

    @PostMapping("/login")
    @RateLimited({RateLimitKey.LOGIN, RateLimitKey.PUBLIC})
    public ResponseEntity<ApiResponse> login(Meta meta, @JsonBody LoginRequest req) {
        List<ApiError> errors = new ArrayList<>();
        if (req.email().isEmpty()) {
            errors.add(field("email", REQUIRED));
        }
        if (req.password().isEmpty()) {
            errors.add(field("password", REQUIRED));
        }
        Validation.check(errors);

        return Responses.success(HttpStatus.OK, authService.login(req), meta);
    }

    @PostMapping("/refresh")
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> refresh(Meta meta, @JsonBody RefreshRequest req) {
        if (req.refreshToken().isEmpty()) {
            Validation.reject("refresh_token", REQUIRED);
        }

        return Responses.success(HttpStatus.OK, authService.refreshToken(req), meta);
    }
}
