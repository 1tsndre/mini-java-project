package io.github.tsndre.minijava.store.service.exception;

/** The requested resource does not exist (404). */
public class NotFoundException extends ServiceException {

    public NotFoundException(String message) {
        super(message);
    }
}
