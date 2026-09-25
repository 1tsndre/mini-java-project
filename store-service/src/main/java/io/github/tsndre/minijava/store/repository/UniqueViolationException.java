package io.github.tsndre.minijava.store.repository;

/** A write violated a unique constraint; the driver error is kept as the cause for logging. */
public class UniqueViolationException extends RuntimeException {

    public UniqueViolationException(Throwable cause) {
        super("duplicate key: " + cause.getMessage(), cause);
    }
}
