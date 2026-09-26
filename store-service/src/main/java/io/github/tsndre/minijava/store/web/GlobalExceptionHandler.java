package io.github.tsndre.minijava.store.web;

import io.github.tsndre.minijava.common.response.ApiError;
import io.github.tsndre.minijava.common.response.ApiResponse;
import io.github.tsndre.minijava.common.response.Responses;
import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.service.exception.ConflictException;
import io.github.tsndre.minijava.store.service.exception.ForbiddenException;
import io.github.tsndre.minijava.store.service.exception.InsufficientStockException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.InvalidStatusException;
import io.github.tsndre.minijava.store.service.exception.NotFoundException;
import io.github.tsndre.minijava.store.service.exception.ServiceException;
import io.github.tsndre.minijava.store.service.exception.UnauthorizedException;
import io.github.tsndre.minijava.store.service.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/** Turns every exception into the JSON envelope, with the status and code the Go service uses. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private record Mapping(HttpStatus status, ErrorCode code) {
    }

    private static final Map<Class<? extends ServiceException>, Mapping> SERVICE_ERRORS = Map.of(
            NotFoundException.class, new Mapping(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND),
            ForbiddenException.class, new Mapping(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN),
            ConflictException.class, new Mapping(HttpStatus.CONFLICT, ErrorCode.CONFLICT),
            ValidationException.class, new Mapping(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION),
            UnauthorizedException.class, new Mapping(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED),
            InternalException.class, new Mapping(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL),
            InsufficientStockException.class, new Mapping(HttpStatus.BAD_REQUEST, ErrorCode.INSUFFICIENT_STOCK),
            InvalidStatusException.class, new Mapping(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_STATUS));

    @ExceptionHandler(ServiceException.class)
    ResponseEntity<ApiResponse> handleServiceException(ServiceException e, HttpServletRequest request) {
        Mapping mapping = SERVICE_ERRORS.getOrDefault(e.getClass(),
                new Mapping(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL));
        return Responses.error(mapping.status(), RequestContext.meta(request),
                ApiError.of(mapping.code().value(), e.getMessage()));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse> handleApiException(ApiException e, HttpServletRequest request) {
        return Responses.error(e.getStatus(), RequestContext.meta(request), e.getErrors().toArray(ApiError[]::new));
    }

    /**
     * Unknown routes answer 404. So does a known path with another method: the Go router has a
     * catch-all route, so it never answers 405.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ApiResponse> handleNotFound(HttpServletRequest request) {
        return handleApiException(ApiException.notFound(), request);
    }

    /** Anything unexpected, the equivalent of a recovered panic in the Go service. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("panic recovered", e);
        return Responses.error(HttpStatus.INTERNAL_SERVER_ERROR, RequestContext.meta(request),
                ApiError.of(ErrorCode.INTERNAL.value(), "internal server error"));
    }
}
