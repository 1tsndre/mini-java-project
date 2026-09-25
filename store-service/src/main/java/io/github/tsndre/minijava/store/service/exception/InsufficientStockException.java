package io.github.tsndre.minijava.store.service.exception;

/** A product cannot cover the requested quantity (400, INSUFFICIENT_STOCK). */
public class InsufficientStockException extends ServiceException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
