package io.github.tsndre.minijava.store.service.exception;

/** The request conflicts with existing data, e.g. a duplicate (409). */
public class ConflictException extends ServiceException {

    public ConflictException(String message) {
        super(message);
    }
}
