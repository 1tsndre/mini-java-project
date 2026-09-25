package io.github.tsndre.minijava.store.dto.request;

/** Missing fields read as empty strings and zeros, like Go's zero values. */
public record RegisterRequest(
        String email,
        String password,
        String name) {

    public RegisterRequest {
        email = email == null ? "" : email;
        password = password == null ? "" : password;
        name = name == null ? "" : name;
    }
}
