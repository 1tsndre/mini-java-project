package io.github.tsndre.minijava.store.service.exception;

/** An expected failure such as an unavailable database, reported with a generic message (500). */
public class InternalException extends ServiceException {

    public InternalException(String message) {
        super(message);
    }
}
