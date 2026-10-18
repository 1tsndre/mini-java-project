package io.github.tsndre.minijava.common.jwt;

/** {@code id} is the token's jti; only refresh tokens have one, so it is "" for an access token. */
public record Claims(String userId, String email, String role, String type, String id) {
}
