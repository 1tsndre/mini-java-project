package io.github.tsndre.minijava.store.service.exception;

/**
 * A business failure the client is told about. The message is part of the API: it is sent as is,
 * so it must never contain internal details.
 */
public abstract class ServiceException extends RuntimeException {

    protected ServiceException(String message) {
        super(message);
    }
}
