package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.upload.UploadException;
import io.github.tsndre.minijava.common.upload.Uploader;
import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.function.Function;

final class Uploads {

    private Uploads() {
    }

    /**
     * Stores the file of the given form field under {@code subDir} and passes its path to
     * {@code use}. If {@code use} fails, the stored file is deleted again.
     */
    static <T> T store(HttpServletRequest request, Uploader uploader, String fieldName, String subDir,
                       Function<String, T> use) {
        MultipartFile file = requireFile(request, fieldName);

        String path;
        try {
            path = uploader.upload(file, subDir);
        } catch (UploadException e) {
            throw ApiException.badRequest(e.getMessage());
        }

        try {
            return use.apply(path);
        } catch (RuntimeException e) {
            try {
                uploader.delete(path);
            } catch (IOException ignored) {
                // Best effort, like the Go service: the error that matters is the one above.
            }
            throw e;
        }
    }

    private static MultipartFile requireFile(HttpServletRequest request, String fieldName) {
        String missing = fieldName + " file is required";

        MultipartHttpServletRequest multipart = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
        if (multipart == null || !isFormData(request.getContentType())) {
            throw ApiException.badRequest(missing);
        }

        MultipartFile file;
        try {
            file = multipart.getFile(fieldName);
        } catch (MaxUploadSizeExceededException e) {
            throw ApiException.of(HttpStatus.CONTENT_TOO_LARGE, ErrorCode.VALIDATION, "request body too large");
        } catch (MultipartException e) {
            throw ApiException.badRequest(missing);
        }
        // A part without a file name is a plain form value, not a file.
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isEmpty()) {
            throw ApiException.badRequest(missing);
        }
        return file;
    }

    private static boolean isFormData(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.MULTIPART_FORM_DATA.equalsTypeAndSubtype(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }
}
