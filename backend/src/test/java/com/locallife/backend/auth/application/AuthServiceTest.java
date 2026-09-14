package com.locallife.backend.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.user.application.PasswordHashingService;
import com.locallife.backend.user.domain.AuthProvider;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.domain.UserIdentity;
import com.locallife.backend.user.infrastructure.UserIdentityRepository;
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
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordHashingService passwordHashingService;

    @Mock
    private GoogleAuthService googleAuthService;

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

    @Test
    void login_ShouldThrow_WithoutCallingPasswordMatcher_WhenAccountIsGoogleOnly() {
        // LL-EF-011 : passwordHash == "" (jamais null, colonne NOT NULL) pour un compte
        // créé exclusivement via Google — voir ADR-0002. Sans cette garde,
        // BCryptPasswordEncoder#matches lèverait une exception sur "" (pas un hash BCrypt
        // valide) au lieu du rejet propre attendu ici.
        User googleOnlyUser = new User(1L, "Jean Dupont", "jean@gmail.com", "", Role.USER, LocalDateTime.now());
        when(userRepository.findByEmail("jean@gmail.com")).thenReturn(Optional.of(googleOnlyUser));

        assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest("jean@gmail.com", "n'importe-quoi")));
        verify(passwordHashingService, never()).matches(any(), any());
    }

    // --- loginOrRegisterWithGoogle (LL-EF-011) ---

    @Test
    void loginOrRegisterWithGoogle_ShouldAuthenticateExistingUser_WhenIdentityAlreadyLinked() {
        // Given : une UserIdentity GOOGLE existe déjà pour ce sub (connexions précédentes).
        User user = new User(1L, "Jean Dupont", "jean@gmail.com", "", Role.USER, LocalDateTime.now());
        UserIdentity existingIdentity = new UserIdentity(10L, 1L, AuthProvider.GOOGLE, "google-sub-123",
                LocalDateTime.now());
        when(googleAuthService.verify("id-token"))
                .thenReturn(new GoogleAuthService.GoogleIdentity("google-sub-123", "jean@gmail.com", "Jean Dupont"));
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(existingIdentity));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(1L, "jean@gmail.com", Role.USER)).thenReturn("un.jwt.token");

        // When
        LoginResponse response = authService.loginOrRegisterWithGoogle("id-token");

        // Then
        assertEquals("un.jwt.token", response.token());
        verify(userRepository, never()).save(any());
        verify(userIdentityRepository, never()).save(any());
    }

    @Test
    void loginOrRegisterWithGoogle_ShouldLinkExistingAccount_WhenEmailMatchesButNoIdentityYet() {
        // Given : "jean" s'est déjà inscrit classiquement avec cet email, se connecte
        // maintenant via Google pour la première fois — critère d'acceptation explicite
        // du ticket (associer plutôt que doublonner).
        User existingLocalUser = new User(1L, "jean", "jean@gmail.com", "hash", Role.USER, LocalDateTime.now());
        when(googleAuthService.verify("id-token"))
                .thenReturn(new GoogleAuthService.GoogleIdentity("google-sub-123", "jean@gmail.com", "Jean Dupont"));
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("jean@gmail.com")).thenReturn(Optional.of(existingLocalUser));
        when(jwtService.generateToken(1L, "jean@gmail.com", Role.USER)).thenReturn("un.jwt.token");

        // When
        LoginResponse response = authService.loginOrRegisterWithGoogle("id-token");

        // Then
        assertEquals("un.jwt.token", response.token());
        verify(userRepository, never()).save(any());
        verify(userIdentityRepository).save(argThat(userIdentity ->
                userIdentity.id() == null
                        && userIdentity.userId().equals(1L)
                        && userIdentity.provider() == AuthProvider.GOOGLE
                        && userIdentity.providerUserId().equals("google-sub-123")));
    }

    @Test
    void loginOrRegisterWithGoogle_ShouldCreateNewUser_WhenNoIdentityAndNoMatchingEmail() {
        // Given : première connexion Google, aucun compte LocalLife préexistant pour cet email.
        when(googleAuthService.verify("id-token"))
                .thenReturn(new GoogleAuthService.GoogleIdentity("google-sub-123", "nouveau@gmail.com", "Nouveau Venu"));
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("nouveau@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User toSave = invocation.getArgument(0);
            return new User(2L, toSave.username(), toSave.email(), toSave.passwordHash(), toSave.role(),
                    toSave.createdAt());
        });
        when(jwtService.generateToken(2L, "nouveau@gmail.com", Role.USER)).thenReturn("un.jwt.token");

        // When
        LoginResponse response = authService.loginOrRegisterWithGoogle("id-token");

        // Then
        assertEquals("un.jwt.token", response.token());
        verify(userRepository).save(argThat(user ->
                user.username().equals("Nouveau Venu")
                        && user.email().equals("nouveau@gmail.com")
                        // Jamais null (colonne NOT NULL) ni un vrai hash BCrypt : aucun mot de
                        // passe LocalLife n'est demandé pour ce compte (exigence du ticket).
                        && user.passwordHash().isEmpty()
                        && user.role() == Role.USER));
        verify(userIdentityRepository).save(argThat(userIdentity ->
                userIdentity.id() == null
                        && userIdentity.userId().equals(2L)
                        && userIdentity.provider() == AuthProvider.GOOGLE
                        && userIdentity.providerUserId().equals("google-sub-123")));
    }

    @Test
    void loginOrRegisterWithGoogle_ShouldFallBackToEmailLocalPart_WhenGoogleNameAbsent() {
        // Given : Google n'a pas fourni de nom affiché (voir GoogleAuthServiceTest,
        // claim "name" absent) — repli sur la partie locale de l'email.
        when(googleAuthService.verify("id-token"))
                .thenReturn(new GoogleAuthService.GoogleIdentity("google-sub-123", "nouveau@gmail.com", null));
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("nouveau@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User toSave = invocation.getArgument(0);
            return new User(2L, toSave.username(), toSave.email(), toSave.passwordHash(), toSave.role(),
                    toSave.createdAt());
        });
        when(jwtService.generateToken(2L, "nouveau@gmail.com", Role.USER)).thenReturn("un.jwt.token");

        // When
        authService.loginOrRegisterWithGoogle("id-token");

        // Then
        verify(userRepository).save(argThat(user -> user.username().equals("nouveau")));
    }

}
