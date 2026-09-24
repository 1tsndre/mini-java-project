package io.github.tsndre.minijava.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiError(
        String code,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) String field,
        String message) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, null, message);
    }

    public static ApiError field(String code, String field, String message) {
        return new ApiError(code, field, message);
    }
}
