package io.github.tsndre.minijava.store.service.exception;

/** The request is invalid (400, VALIDATION_ERROR). */
public class ValidationException extends ServiceException {

    public ValidationException(String message) {
        super(message);
    }
}
