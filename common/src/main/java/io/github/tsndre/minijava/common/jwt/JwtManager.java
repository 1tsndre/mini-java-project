package io.github.tsndre.minijava.common.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class JwtManager {

    private static final String CLAIM_USER_ID = "user_id";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";

    private final Algorithm signingAlgorithm;
    /** Like the Go service, any HMAC algorithm is accepted when validating, keyed by the "alg" header. */
    private final Map<String, JWTVerifier> verifiers;
    private final Duration accessExpiry;
    private final Duration refreshExpiry;

    public JwtManager(String secret, Duration accessExpiry, Duration refreshExpiry) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        this.signingAlgorithm = Algorithm.HMAC256(key);
        this.verifiers = Map.of(
                "HS256", verifier(signingAlgorithm),
                "HS384", verifier(Algorithm.HMAC384(key)),
                "HS512", verifier(Algorithm.HMAC512(key)));
        this.accessExpiry = accessExpiry;
        this.refreshExpiry = refreshExpiry;
    }

    public TokenPair generateTokenPair(String userId, String email, String role) {
        String accessToken = generateToken(userId, email, role, TokenType.ACCESS, accessExpiry);
        String refreshToken = generateToken(userId, email, role, TokenType.REFRESH, refreshExpiry);
        return new TokenPair(accessToken, refreshToken);
    }

    /**
     * Returns the claims of a valid token.
     *
     * @throws ExpiredTokenException if the token has expired
     * @throws InvalidTokenException if the token is malformed or its signature does not match
     */
    public Claims validateToken(String token) {
        DecodedJWT decoded;
        try {
            JWTVerifier verifier = verifiers.get(JWT.decode(token).getAlgorithm());
            if (verifier == null) {
                throw new InvalidTokenException();
            }
            decoded = verifier.verify(token);
        } catch (TokenExpiredException e) {
            throw new ExpiredTokenException();
        } catch (JWTVerificationException e) {
            throw new InvalidTokenException();
        }
        return new Claims(
                claim(decoded, CLAIM_USER_ID),
                claim(decoded, CLAIM_EMAIL),
                claim(decoded, CLAIM_ROLE),
                claim(decoded, CLAIM_TYPE));
    }

    private String generateToken(String userId, String email, String role, TokenType type, Duration expiry) {
        Instant now = Instant.now();
        return JWT.create()
                .withClaim(CLAIM_USER_ID, userId)
                .withClaim(CLAIM_EMAIL, email)
                .withClaim(CLAIM_ROLE, role)
                .withClaim(CLAIM_TYPE, type.value())
                .withExpiresAt(now.plus(expiry))
                .withIssuedAt(now)
                .sign(signingAlgorithm);
    }

    /** The Go service does not check "iat", only "exp" and "nbf". */
    private static JWTVerifier verifier(Algorithm algorithm) {
        return JWT.require(algorithm).ignoreIssuedAt().build();
    }

    /** A missing or null claim reads as ""; a claim of another type makes the token invalid, as in Go. */
    private static String claim(DecodedJWT decoded, String name) {
        Claim claim = decoded.getClaim(name);
        if (claim.isMissing() || claim.isNull()) {
            return "";
        }
        String value = claim.asString();
        if (value == null) {
            throw new InvalidTokenException();
        }
        return value;
    }
}
