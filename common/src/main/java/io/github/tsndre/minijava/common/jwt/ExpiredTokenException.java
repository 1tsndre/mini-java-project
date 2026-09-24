package io.github.tsndre.minijava.common.jwt;

public class ExpiredTokenException extends InvalidTokenException {

    public ExpiredTokenException() {
        super("token has expired");
    }
}
