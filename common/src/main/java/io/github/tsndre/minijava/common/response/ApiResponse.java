package io.github.tsndre.minijava.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record ApiResponse(
        @JsonInclude(JsonInclude.Include.NON_NULL) Object data,
        Meta meta,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ApiError> errors) {
}
