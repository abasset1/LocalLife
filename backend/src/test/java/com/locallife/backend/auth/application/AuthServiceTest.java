package com.locallife.backend.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.user.application.PasswordHashingService;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordHashingService passwordHashingService;

    @InjectMocks
    private AuthService authService;

    // --- register ---

    @Test
    void register_ShouldHashPasswordAndSaveUser_WhenInputsAreValid() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordHashingService.hash("motDePasse123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = authService.register("alice", "alice@example.com", "motDePasse123");

        assertEquals("alice", created.username());
        assertEquals("alice@example.com", created.email());
        assertEquals("hashed", created.passwordHash());
        assertEquals(Role.USER, created.role());
    }

    @Test
    void register_ShouldThrow_WhenEmailAlreadyUsed() {
        User existing = new User(1L, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> authService.register("alice2", "alice@example.com", "motDePasse123"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_ShouldThrow_WhenEmailIsInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.register("alice", "pas-un-email", "motDePasse123"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_ShouldThrow_WhenPasswordIsTooShort() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.register("alice", "alice@example.com", "court"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_ShouldThrow_WhenUsernameIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.register("  ", "alice@example.com", "motDePasse123"));
        verify(userRepository, never()).save(any());
    }

    // --- bootstrapFirstAdmin ---

    @Test
    void bootstrapFirstAdmin_ShouldCreateAdmin_WhenNoAdminExists() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordHashingService.hash("motDePasseAdmin1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<User> created = authService.bootstrapFirstAdmin("admin", "admin@example.com", "motDePasseAdmin1");

        assertEquals(true, created.isPresent());
        assertEquals(Role.ADMIN, created.get().role());
        assertEquals("hashed", created.get().passwordHash());
    }

    @Test
    void bootstrapFirstAdmin_ShouldDoNothing_WhenAnAdminAlreadyExists() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        Optional<User> result = authService.bootstrapFirstAdmin("admin", "admin@example.com", "motDePasseAdmin1");

        assertEquals(true, result.isEmpty());
        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void bootstrapFirstAdmin_ShouldThrow_WhenPasswordIsTooShort() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> authService.bootstrapFirstAdmin("admin", "admin@example.com", "court"));
        verify(userRepository, never()).save(any());
    }

    // --- login ---

    @Test
    void login_ShouldReturnToken_WhenCredentialsAreCorrect() {
        User user = new User(1L, "bob", "bob@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
        when(passwordHashingService.matches("motDePasse123", "hash")).thenReturn(true);
        when(jwtService.generateToken(1L, "bob@example.com", Role.USER)).thenReturn("un.jwt.token");

        LoginResponse response = authService.login(new LoginRequest("bob@example.com", "motDePasse123"));

        assertNotNull(response.token());
        assertEquals("un.jwt.token", response.token());
    }

    @Test
    void login_ShouldThrow_WhenEmailDoesNotExist() {
        when(userRepository.findByEmail("inconnu@example.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("inconnu@example.com", "motDePasse123")));
    }

    @Test
    void login_ShouldThrow_WhenPasswordIsIncorrect() {
        User user = new User(1L, "bob", "bob@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
        when(passwordHashingService.matches("mauvaisMotDePasse", "hash")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("bob@example.com", "mauvaisMotDePasse")));
    }

}
