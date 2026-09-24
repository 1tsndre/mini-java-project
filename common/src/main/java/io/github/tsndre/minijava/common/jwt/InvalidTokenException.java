package io.github.tsndre.minijava.common.jwt;

public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("invalid token");
    }

    protected InvalidTokenException(String message) {
        super(message);
    }
}
