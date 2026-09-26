package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.response.ApiError;
import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.constant.ValidationLimit;
import io.github.tsndre.minijava.store.util.GoStrings;
import io.github.tsndre.minijava.store.web.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.List;

final class Validation {

    static final String REQUIRED = "is required";
    static final String MAX_VARCHAR_MESSAGE = "maximum " + ValidationLimit.MAX_VARCHAR_LENGTH + " characters";
    static final String MAX_PASSWORD_MESSAGE = "maximum " + ValidationLimit.MAX_PASSWORD_BYTES + " bytes";

    private Validation() {
    }

    static ApiError field(String field, String message) {
        return ApiError.field(ErrorCode.VALIDATION.value(), field, message);
    }

    static void check(List<ApiError> errors) {
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }

    static void reject(String field, String message) {
        throw ApiException.validation(List.of(field(field, message)));
    }

    /** Whether s is longer than a VARCHAR(255) column holds; PostgreSQL counts characters, not bytes. */
    static boolean exceedsVarchar(String s) {
        return GoStrings.runeCount(s) > ValidationLimit.MAX_VARCHAR_LENGTH;
    }

    /** The length bcrypt sees: UTF-8 bytes. */
    static int byteLength(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}
