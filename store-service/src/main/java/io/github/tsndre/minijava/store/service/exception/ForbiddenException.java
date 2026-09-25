package io.github.tsndre.minijava.store.service.exception;

/** The user may not act on the resource (403). */
public class ForbiddenException extends ServiceException {

    public ForbiddenException(String message) {
        super(message);
    }
}
