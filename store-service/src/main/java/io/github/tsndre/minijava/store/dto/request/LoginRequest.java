package io.github.tsndre.minijava.store.dto.request;

/** Missing fields read as empty strings and zeros, like Go's zero values. */
public record LoginRequest(
        String email,
        String password) {

    public LoginRequest {
        email = email == null ? "" : email;
        password = password == null ? "" : password;
    }
}
