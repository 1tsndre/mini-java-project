package io.github.tsndre.minijava.store.web;

import io.github.tsndre.minijava.common.response.ApiError;
import io.github.tsndre.minijava.store.constant.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<ApiError> errors;

    private ApiException(HttpStatus status, List<ApiError> errors) {
        super(errors.getFirst().message());
        this.status = status;
        this.errors = errors;
    }

    public static ApiException of(HttpStatus status, ErrorCode code, String message) {
        return new ApiException(status, List.of(ApiError.of(code.value(), message)));
    }

    public static ApiException badRequest(String message) {
        return of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, message);
    }

    public static ApiException validation(List<ApiError> errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, List.copyOf(errors));
    }

    public static ApiException unauthorized(String message) {
        return of(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, message);
    }

    public static ApiException notFound() {
        return of(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "not found");
    }
}
