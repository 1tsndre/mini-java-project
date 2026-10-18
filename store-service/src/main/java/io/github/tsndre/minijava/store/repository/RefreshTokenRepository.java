package io.github.tsndre.minijava.store.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * The refresh tokens that may still be used, by jti, so a refresh token can be used only once and
 * revoked on logout. Every method throws if the store is unavailable.
 */
public interface RefreshTokenRepository {

    /** Stores the token for userId until ttl passes. */
    void save(String tokenId, UUID userId, Duration ttl);

    /**
     * Removes the token and returns its user; empty for a token that was never stored, has expired,
     * or was already used or revoked.
     */
    Optional<UUID> consume(String tokenId);

    void revoke(String tokenId);
}
