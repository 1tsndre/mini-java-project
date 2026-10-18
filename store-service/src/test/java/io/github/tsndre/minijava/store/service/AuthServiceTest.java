package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.common.jwt.JwtManager;
import io.github.tsndre.minijava.common.jwt.TokenPair;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.request.LoginRequest;
import io.github.tsndre.minijava.store.dto.request.LogoutRequest;
import io.github.tsndre.minijava.store.dto.request.RefreshRequest;
import io.github.tsndre.minijava.store.dto.request.RegisterRequest;
import io.github.tsndre.minijava.store.dto.response.UserResponse;
import io.github.tsndre.minijava.store.model.User;
import io.github.tsndre.minijava.store.repository.RefreshTokenRepository;
import io.github.tsndre.minijava.store.repository.UserRepository;
import io.github.tsndre.minijava.store.service.exception.ConflictException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.UnauthorizedException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "password123";
    private static final String HASHED_PASSWORD =
            BCrypt.hashpw(PASSWORD.getBytes(StandardCharsets.UTF_8), BCrypt.gensalt(4));

    private static final Duration REFRESH_EXPIRY = Duration.ofHours(168);

    private final JwtManager jwtManager = new JwtManager("test-secret", Duration.ofMinutes(15), REFRESH_EXPIRY);

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AuthService service() {
        return new AuthService(userRepository, refreshTokenRepository, jwtManager);
    }

    private static User storedUser(String email, Role role) {
        return User.builder().id(UUID.randomUUID()).email(email).password(HASHED_PASSWORD).role(role).build();
    }

    @Nested
    class Register {

        private final RegisterRequest req = new RegisterRequest("test@example.com", PASSWORD, "Test User");

        @Test
        void success() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
            when(userRepository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

            UserResponse resp = service().register(req);

            assertThat(resp.email()).isEqualTo("test@example.com");
            assertThat(resp.name()).isEqualTo("Test User");
            assertThat(resp.role()).isEqualTo(Role.BUYER);
        }

        @Test
        void emailAlreadyRegistered() {
            when(userRepository.findByEmail("existing@example.com"))
                    .thenReturn(Optional.of(storedUser("existing@example.com", Role.BUYER)));

            assertThatThrownBy(() -> service().register(new RegisterRequest("existing@example.com", PASSWORD, "Test User")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("email already registered");
        }

        @Test
        void createUserFails() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
            when(userRepository.create(any())).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> service().register(req))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to create user");
        }

        @Test
        void concurrentRegistrationWithSameEmail() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
            when(userRepository.create(any())).thenThrow(TestErrors.duplicateKey());

            assertThatThrownBy(() -> service().register(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("email already registered");
        }

        @Test
        void lookupFailureIsTreatedAsNoAccount() {
            when(userRepository.findByEmail("test@example.com")).thenThrow(TestErrors.dbError());
            when(userRepository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(service().register(req).email()).isEqualTo("test@example.com");
        }

        @Test
        void passwordIsStoredAsBcryptHash() {
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
            ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
            when(userRepository.create(created.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            service().register(req);

            assertThat(created.getValue().getPassword()).startsWith("$2a$10$");
            assertThat(BCrypt.checkpw(PASSWORD.getBytes(StandardCharsets.UTF_8), created.getValue().getPassword())).isTrue();
        }
    }

    @Nested
    class Login {

        @Test
        void successStoresTheRefreshToken() {
            User user = storedUser("test@example.com", Role.BUYER);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            TokenPair pair = service().login(new LoginRequest("test@example.com", PASSWORD));

            assertThat(pair.accessToken()).isNotEmpty();
            assertThat(pair.refreshToken()).isNotEmpty();
            assertThat(jwtManager.validateToken(pair.refreshToken()).id()).isEqualTo(pair.refreshTokenId());
            assertThat(jwtManager.validateToken(pair.accessToken()).id()).isEmpty();
            verify(refreshTokenRepository).save(pair.refreshTokenId(), user.getId(), REFRESH_EXPIRY);
        }

        @Test
        void refreshTokenCannotBeStored() {
            User user = storedUser("test@example.com", Role.BUYER);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            doThrow(new IllegalStateException("redis down"))
                    .when(refreshTokenRepository).save(any(), eq(user.getId()), eq(REFRESH_EXPIRY));

            assertThatThrownBy(() -> service().login(new LoginRequest("test@example.com", PASSWORD)))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("internal server error");
        }

        @Test
        void invalidEmail() {
            when(userRepository.findByEmail("wrong@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().login(new LoginRequest("wrong@example.com", PASSWORD)))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("invalid email or password");
        }

        @Test
        void wrongPassword() {
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(storedUser("test@example.com", Role.BUYER)));

            assertThatThrownBy(() -> service().login(new LoginRequest("test@example.com", "wrongpassword")))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("invalid email or password");
        }
    }

    @Nested
    class RefreshToken {

        private final UUID userId = UUID.randomUUID();
        private final TokenPair pair = jwtManager.generateTokenPair(userId.toString(), "test@example.com", Role.BUYER.value());

        @Test
        void successReloadsTheRoleAndRotatesTheToken() {
            when(refreshTokenRepository.consume(pair.refreshTokenId())).thenReturn(Optional.of(userId));
            when(userRepository.findById(userId)).thenReturn(Optional.of(
                    User.builder().id(userId).email("test@example.com").role(Role.SELLER).build()));

            TokenPair refreshed = service().refreshToken(new RefreshRequest(pair.refreshToken()));

            assertThat(jwtManager.validateToken(refreshed.accessToken()).role()).isEqualTo(Role.SELLER.value());
            assertThat(refreshed.refreshTokenId()).isNotEqualTo(pair.refreshTokenId());
            verify(refreshTokenRepository).save(refreshed.refreshTokenId(), userId, REFRESH_EXPIRY);
        }

        @Test
        void alreadyUsedOrRevoked() {
            when(refreshTokenRepository.consume(pair.refreshTokenId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().refreshToken(new RefreshRequest(pair.refreshToken())))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("invalid refresh token");
        }

        @Test
        void storedForAnotherUser() {
            when(refreshTokenRepository.consume(pair.refreshTokenId())).thenReturn(Optional.of(UUID.randomUUID()));

            assertThatThrownBy(() -> service().refreshToken(new RefreshRequest(pair.refreshToken())))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("invalid refresh token");
        }

        @Test
        void tokenStoreUnavailable() {
            when(refreshTokenRepository.consume(pair.refreshTokenId())).thenThrow(new IllegalStateException("redis down"));

            assertThatThrownBy(() -> service().refreshToken(new RefreshRequest(pair.refreshToken())))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("internal server error");
        }

        @Test
        void accessTokenRejected() {
            assertThatThrownBy(() -> service().refreshToken(new RefreshRequest(pair.accessToken())))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("invalid refresh token");
        }

        @Test
        void malformedToken() {
            assertThatThrownBy(() -> service().refreshToken(new RefreshRequest("not-a-token")))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("invalid refresh token");
        }

        @Test
        void userNoLongerExists() {
            when(refreshTokenRepository.consume(pair.refreshTokenId())).thenReturn(Optional.of(userId));
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().refreshToken(new RefreshRequest(pair.refreshToken())))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("invalid refresh token");
        }
    }

    @Nested
    class Logout {

        private final TokenPair pair =
                jwtManager.generateTokenPair(UUID.randomUUID().toString(), "test@example.com", Role.BUYER.value());

        @Test
        void success() {
            service().logout(new LogoutRequest(pair.refreshToken()));

            verify(refreshTokenRepository).revoke(pair.refreshTokenId());
        }

        @Test
        void accessTokenRejected() {
            assertThatThrownBy(() -> service().logout(new LogoutRequest(pair.accessToken())))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("invalid refresh token");
        }

        @Test
        void malformedToken() {
            assertThatThrownBy(() -> service().logout(new LogoutRequest("not-a-token")))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("invalid refresh token");
        }

        @Test
        void tokenStoreUnavailable() {
            doThrow(new IllegalStateException("redis down")).when(refreshTokenRepository).revoke(pair.refreshTokenId());

            assertThatThrownBy(() -> service().logout(new LogoutRequest(pair.refreshToken())))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("internal server error");
        }
    }

    /** With a working token store: a refresh token works once, and not after logout. */
    @Test
    void refreshTokenRotation() {
        User user = storedUser("test@example.com", Role.BUYER);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        AuthService service = new AuthService(userRepository, new InMemoryRefreshTokenRepository(), jwtManager);

        TokenPair first = service.login(new LoginRequest("test@example.com", PASSWORD));
        TokenPair second = service.refreshToken(new RefreshRequest(first.refreshToken()));

        assertThatThrownBy(() -> service.refreshToken(new RefreshRequest(first.refreshToken())))
                .as("a refresh token works only once")
                .hasMessage("invalid refresh token");

        service.logout(new LogoutRequest(second.refreshToken()));
        assertThatThrownBy(() -> service.refreshToken(new RefreshRequest(second.refreshToken())))
                .as("logout revokes the refresh token")
                .hasMessage("invalid refresh token");
    }

    private static final class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

        private final Map<String, UUID> sessions = new ConcurrentHashMap<>();

        @Override
        public void save(String tokenId, UUID userId, Duration ttl) {
            sessions.put(tokenId, userId);
        }

        @Override
        public Optional<UUID> consume(String tokenId) {
            return Optional.ofNullable(sessions.remove(tokenId));
        }

        @Override
        public void revoke(String tokenId) {
            sessions.remove(tokenId);
        }
    }

    @Nested
    class EmailIsCaseInsensitive {

        @Test
        void registerStoresTheEmailInLowerCase() {
            when(userRepository.findByEmail("andreas@example.com")).thenReturn(Optional.empty());
            ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
            when(userRepository.create(created.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            UserResponse resp = service().register(new RegisterRequest("Andreas@Example.COM", PASSWORD, "Andreas"));

            assertThat(resp.email()).isEqualTo("andreas@example.com");
            assertThat(created.getValue().getEmail()).isEqualTo("andreas@example.com");
        }

        @Test
        void registerRejectsACaseVariantOfAnExistingEmail() {
            when(userRepository.findByEmail("andreas@example.com"))
                    .thenReturn(Optional.of(storedUser("andreas@example.com", Role.BUYER)));

            assertThatThrownBy(() -> service().register(new RegisterRequest("ANDREAS@example.com", PASSWORD, "Andreas")))
                    .hasMessage("email already registered");
        }

        @Test
        void loginMatchesRegardlessOfCase() {
            when(userRepository.findByEmail("andreas@example.com"))
                    .thenReturn(Optional.of(storedUser("andreas@example.com", Role.BUYER)));

            assertThat(service().login(new LoginRequest("Andreas@Example.com", PASSWORD))).isNotNull();
            verify(userRepository).findByEmail("andreas@example.com");
        }

        @Test
        void lowerCasingFollowsGoRatherThanJavaLocaleRules() {
            assertThat(AuthService.normalizeEmail("İ@X.COM")).isEqualTo("i@x.com");
        }
    }
}
