package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.common.jwt.JwtManager;
import io.github.tsndre.minijava.common.jwt.TokenPair;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.request.LoginRequest;
import io.github.tsndre.minijava.store.dto.request.RefreshRequest;
import io.github.tsndre.minijava.store.dto.request.RegisterRequest;
import io.github.tsndre.minijava.store.dto.response.UserResponse;
import io.github.tsndre.minijava.store.model.User;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "password123";
    private static final String HASHED_PASSWORD =
            BCrypt.hashpw(PASSWORD.getBytes(StandardCharsets.UTF_8), BCrypt.gensalt(4));

    private final JwtManager jwtManager = new JwtManager("test-secret", Duration.ofMinutes(15), Duration.ofHours(168));

    @Mock
    private UserRepository userRepository;

    private AuthService service() {
        return new AuthService(userRepository, jwtManager);
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
        void success() {
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(storedUser("test@example.com", Role.BUYER)));

            TokenPair pair = service().login(new LoginRequest("test@example.com", PASSWORD));

            assertThat(pair.accessToken()).isNotEmpty();
            assertThat(pair.refreshToken()).isNotEmpty();
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
        void successReloadsTheRoleFromTheDatabase() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(
                    User.builder().id(userId).email("test@example.com").role(Role.SELLER).build()));

            TokenPair refreshed = service().refreshToken(new RefreshRequest(pair.refreshToken()));

            assertThat(jwtManager.validateToken(refreshed.accessToken()).role()).isEqualTo(Role.SELLER.value());
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
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service().refreshToken(new RefreshRequest(pair.refreshToken())))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("invalid refresh token");
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
