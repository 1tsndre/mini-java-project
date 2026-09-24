package io.github.tsndre.minijava.common.jwt;

/** Values of the {@code type} claim, so a refresh token cannot be used as an access token. */
public enum TokenType {

    ACCESS("access"),
    REFRESH("refresh");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
