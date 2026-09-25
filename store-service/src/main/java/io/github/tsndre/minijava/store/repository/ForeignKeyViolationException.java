package io.github.tsndre.minijava.store.repository;

/**
 * A write referenced a row that does not exist, or a delete removed a row that is still
 * referenced. The driver error is kept as the cause for logging.
 */
public class ForeignKeyViolationException extends RuntimeException {

    public ForeignKeyViolationException(Throwable cause) {
        super("foreign key violation: " + cause.getMessage(), cause);
    }
}
