package io.github.tsndre.minijava.store.service.exception;

/** An order cannot make the requested status change (400, INVALID_STATUS). */
public class InvalidStatusException extends ServiceException {

    public InvalidStatusException(String message) {
        super(message);
    }
}
