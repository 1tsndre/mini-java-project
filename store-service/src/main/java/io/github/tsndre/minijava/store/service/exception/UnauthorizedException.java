package io.github.tsndre.minijava.store.service.exception;

/** The credentials or token are not valid (401). */
public class UnauthorizedException extends ServiceException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
