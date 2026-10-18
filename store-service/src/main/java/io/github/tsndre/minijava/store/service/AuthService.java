package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.common.jwt.Claims;
import io.github.tsndre.minijava.common.jwt.JwtManager;
import io.github.tsndre.minijava.common.jwt.TokenPair;
import io.github.tsndre.minijava.common.jwt.TokenType;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.request.LoginRequest;
import io.github.tsndre.minijava.store.dto.request.LogoutRequest;
import io.github.tsndre.minijava.store.dto.request.RefreshRequest;
import io.github.tsndre.minijava.store.dto.request.RegisterRequest;
import io.github.tsndre.minijava.store.dto.response.UserResponse;
import io.github.tsndre.minijava.store.model.User;
import io.github.tsndre.minijava.store.repository.RefreshTokenRepository;
import io.github.tsndre.minijava.store.repository.UniqueViolationException;
import io.github.tsndre.minijava.store.repository.UserRepository;
import io.github.tsndre.minijava.store.service.exception.ConflictException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.UnauthorizedException;
import io.github.tsndre.minijava.store.util.GoStrings;
import io.github.tsndre.minijava.store.util.Uuids;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** bcrypt's default cost in the Go service. */
    private static final int BCRYPT_COST = 10;
    private static final String BCRYPT_VERSION = "$2a";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtManager jwtManager;

    /**
     * Emails are effectively case-insensitive (mobile keyboards capitalise the first letter), so an
     * account registered as "Andreas@x.com" must be reachable as "andreas@x.com" and must not be
     * registrable twice.
     */
    static String normalizeEmail(String email) {
        return GoStrings.toLower(email);
    }

    public UserResponse register(RegisterRequest req) {
        String email = normalizeEmail(req.email());

        if (findByEmail(email).isPresent()) {
            throw new ConflictException("email already registered");
        }

        String hashedPassword;
        try {
            hashedPassword = BCrypt.hashpw(req.password().getBytes(StandardCharsets.UTF_8),
                    BCrypt.gensalt(BCRYPT_VERSION, BCRYPT_COST));
        } catch (IllegalArgumentException e) {
            log.error("failed to hash password", e);
            throw new InternalException("internal server error");
        }

        User user = User.builder()
                .email(email)
                .password(hashedPassword)
                .name(req.name())
                .role(Role.BUYER)
                .build();

        try {
            user = userRepository.create(user);
        } catch (UniqueViolationException e) {
            // A concurrent registration with the same email passed the check above.
            throw new ConflictException("email already registered");
        } catch (RuntimeException e) {
            log.error("failed to create user", e);
            throw new InternalException("failed to create user");
        }

        log.atInfo()
                .addKeyValue("user_id", user.getId())
                .addKeyValue("email", user.getEmail())
                .log("user registered");

        return user.toResponse();
    }

    public TokenPair login(LoginRequest req) {
        User user = findByEmail(normalizeEmail(req.email()))
                .orElseThrow(() -> new UnauthorizedException("invalid email or password"));

        if (!passwordMatches(user.getPassword(), req.password())) {
            throw new UnauthorizedException("invalid email or password");
        }

        TokenPair tokenPair = issueTokens(user);

        log.atInfo().addKeyValue("user_id", user.getId()).log("user logged in");

        return tokenPair;
    }

    public TokenPair refreshToken(RefreshRequest req) {
        Claims claims;
        try {
            claims = jwtManager.validateToken(req.refreshToken());
        } catch (RuntimeException e) {
            throw new UnauthorizedException("invalid refresh token");
        }

        if (!TokenType.REFRESH.value().equals(claims.type())) {
            throw new UnauthorizedException("invalid refresh token");
        }

        UUID userId = Uuids.parse(claims.userId())
                .orElseThrow(() -> new UnauthorizedException("invalid refresh token"));

        // Each refresh token works once: taking it out of the store before the next pair is issued
        // means a token replayed after its owner refreshed, or after logout, is refused.
        Optional<UUID> ownerId;
        try {
            ownerId = refreshTokenRepository.consume(claims.id());
        } catch (RuntimeException e) {
            log.error("failed to consume refresh token", e);
            throw new InternalException("internal server error");
        }
        if (!ownerId.map(userId::equals).orElse(false)) {
            throw new UnauthorizedException("invalid refresh token");
        }

        // Reload the user so the new tokens carry the current role (e.g. buyer -> seller after
        // creating a store) instead of the one baked into the old refresh token.
        User user;
        try {
            user = userRepository.findById(userId).orElseThrow();
        } catch (RuntimeException e) {
            throw new UnauthorizedException("invalid refresh token");
        }

        return issueTokens(user);
    }

    /**
     * Revokes the refresh token, so its session cannot be refreshed again. The access tokens already
     * issued stay valid until they expire.
     */
    public void logout(LogoutRequest req) {
        Claims claims;
        try {
            claims = jwtManager.validateToken(req.refreshToken());
        } catch (RuntimeException e) {
            throw new UnauthorizedException("invalid refresh token");
        }
        if (!TokenType.REFRESH.value().equals(claims.type())) {
            throw new UnauthorizedException("invalid refresh token");
        }

        try {
            refreshTokenRepository.revoke(claims.id());
        } catch (RuntimeException e) {
            log.error("failed to revoke refresh token", e);
            throw new InternalException("internal server error");
        }

        log.atInfo().addKeyValue("user_id", claims.userId()).log("user logged out");
    }

    /** A lookup failure counts as "no such user", like the Go service. */
    private Optional<User> findByEmail(String email) {
        try {
            return userRepository.findByEmail(email);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean passwordMatches(String hashedPassword, String password) {
        try {
            return BCrypt.checkpw(password.getBytes(StandardCharsets.UTF_8), hashedPassword);
        } catch (IllegalArgumentException e) {
            // A malformed stored hash never matches.
            return false;
        }
    }

    /** Generates a token pair for the user and stores its refresh token. */
    private TokenPair issueTokens(User user) {
        TokenPair tokenPair;
        try {
            tokenPair = jwtManager.generateTokenPair(user.getId().toString(), user.getEmail(), user.getRole().value());
        } catch (RuntimeException e) {
            log.error("failed to generate token pair", e);
            throw new InternalException("internal server error");
        }

        try {
            refreshTokenRepository.save(tokenPair.refreshTokenId(), user.getId(), jwtManager.refreshExpiry());
        } catch (RuntimeException e) {
            log.error("failed to store refresh token", e);
            throw new InternalException("internal server error");
        }
        return tokenPair;
    }
}
