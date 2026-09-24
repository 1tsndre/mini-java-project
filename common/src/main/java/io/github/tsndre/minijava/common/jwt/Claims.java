package io.github.tsndre.minijava.common.jwt;

public record Claims(String userId, String email, String role, String type) {
}
