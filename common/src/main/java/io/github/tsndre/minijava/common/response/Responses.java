package io.github.tsndre.minijava.common.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

public final class Responses {

    private Responses() {
    }

    public static ResponseEntity<ApiResponse> success(HttpStatus status, Object data, Meta meta) {
        return json(status, new ApiResponse(data, meta, null));
    }

    public static ResponseEntity<ApiResponse> successWithPagination(HttpStatus status, Object data, Meta meta,
                                                                    Pagination pagination) {
        return success(status, data, meta.withPagination(pagination));
    }

    public static ResponseEntity<ApiResponse> error(HttpStatus status, Meta meta, ApiError... errors) {
        return json(status, new ApiResponse(null, meta, List.of(errors)));
    }

    public static ResponseEntity<ApiResponse> validationError(Meta meta, List<ApiError> errors) {
        return json(HttpStatus.BAD_REQUEST, new ApiResponse(null, meta, errors));
    }

    private static ResponseEntity<ApiResponse> json(HttpStatus status, ApiResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
