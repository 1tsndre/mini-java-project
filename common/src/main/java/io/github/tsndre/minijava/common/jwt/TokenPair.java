package io.github.tsndre.minijava.common.jwt;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** {@code refreshTokenId} is the refresh token's jti, the key its session is stored under. */
public record TokenPair(String accessToken, String refreshToken, @JsonIgnore String refreshTokenId) {
}
