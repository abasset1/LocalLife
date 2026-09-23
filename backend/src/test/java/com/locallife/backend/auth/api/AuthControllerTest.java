package com.locallife.backend.auth.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.auth.application.AuthService;
import com.locallife.backend.auth.application.EmailNotVerifiedException;
import com.locallife.backend.auth.application.EmailVerificationService;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AuthController authController;

    @Test
    void register_ShouldReturnCreated_WithUserWithoutPasswordHash() {
        User created = new User(1L, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now(), true);
        when(authService.register("alice", "alice@example.com", "motDePasse123")).thenReturn(created);

        ResponseEntity<Object> response = authController.register(
                new RegisterRequest("alice", "alice@example.com", "motDePasse123"), httpServletRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        UserResponse body = assertInstanceOf(UserResponse.class, response.getBody());
        assertEquals("alice", body.username());
    }

    @Test
    void register_ShouldReturnBadRequest_WhenServiceThrows() {
        when(authService.register("alice", "email-invalide", "motDePasse123"))
                .thenThrow(new IllegalArgumentException("L'adresse email n'est pas valide."));
        when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/auth/register");

        ResponseEntity<Object> response = authController.register(
                new RegisterRequest("alice", "email-invalide", "motDePasse123"), httpServletRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("L'adresse email n'est pas valide.", body.message());
    }

    @Test
    void login_ShouldReturnOk_WithToken() {
        when(authService.login(new LoginRequest("bob@example.com", "motDePasse123")))
                .thenReturn(new LoginResponse("un.jwt.token"));

        ResponseEntity<Object> response = authController.login(
                new LoginRequest("bob@example.com", "motDePasse123"), httpServletRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        LoginResponse body = assertInstanceOf(LoginResponse.class, response.getBody());
        assertEquals("un.jwt.token", body.token());
    }

    @Test
    void login_ShouldReturnUnauthorized_WhenCredentialsAreIncorrect() {
        when(authService.login(new LoginRequest("bob@example.com", "mauvais")))
                .thenThrow(new IllegalArgumentException("Email ou mot de passe incorrect"));
        when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/auth/login");

        ResponseEntity<Object> response = authController.login(
                new LoginRequest("bob@example.com", "mauvais"), httpServletRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("Email ou mot de passe incorrect", body.message());
    }

    @Test
    void login_ShouldReturnForbidden_WhenEmailIsNotVerified() {
        when(authService.login(new LoginRequest("bob@example.com", "motDePasse123")))
                .thenThrow(new EmailNotVerifiedException());
        when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/auth/login");

        ResponseEntity<Object> response = authController.login(
                new LoginRequest("bob@example.com", "motDePasse123"), httpServletRequest);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertInstanceOf(ErrorResponse.class, response.getBody());
    }

    // --- verifyEmail / resendVerification ---

    @Test
    void verifyEmail_ShouldReturnOk_WhenTokenIsValid() {
        ResponseEntity<Object> response = authController.verifyEmail(
                new VerifyEmailRequest("un-token"), httpServletRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(emailVerificationService).verify("un-token");
    }

    @Test
    void verifyEmail_ShouldReturnBadRequest_WhenTokenIsInvalid() {
        doThrow(new IllegalArgumentException("Ce lien de confirmation est invalide, expiré, ou a déjà été utilisé."))
                .when(emailVerificationService).verify("mauvais");
        when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/auth/verify-email");

        ResponseEntity<Object> response = authController.verifyEmail(
                new VerifyEmailRequest("mauvais"), httpServletRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertInstanceOf(ErrorResponse.class, response.getBody());
    }

    @Test
    void resendVerification_ShouldReturnGenericOk() {
        ResponseEntity<Object> response = authController.resendVerification(
                new ResendVerificationRequest("bob@example.com"), httpServletRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(emailVerificationService).resend("bob@example.com");
    }

    @Test
    void resendVerification_ShouldReturnBadRequest_WhenEmailMissing() {
        doThrow(new IllegalArgumentException("L'adresse email est requise."))
                .when(emailVerificationService).resend(null);
        when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/auth/resend-verification");

        ResponseEntity<Object> response = authController.resendVerification(
                new ResendVerificationRequest(null), httpServletRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // --- loginWithGoogle (LL-EF-011) ---

    @Test
    void loginWithGoogle_ShouldReturnOk_WithToken() {
        when(authService.loginOrRegisterWithGoogle("valid-id-token"))
                .thenReturn(new LoginResponse("un.jwt.token"));

        ResponseEntity<Object> response = authController.loginWithGoogle(
                new GoogleLoginRequest("valid-id-token"), httpServletRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        LoginResponse body = assertInstanceOf(LoginResponse.class, response.getBody());
        assertEquals("un.jwt.token", body.token());
    }

    @Test
    void loginWithGoogle_ShouldReturnUnauthorized_WhenTokenIsInvalid() {
        // Même posture que login() classique : un token Google invalide est un échec
        // d'authentification (401), pas une erreur de saisie (voir la javadoc du
        // contrôleur pour le détail de ce choix).
        when(authService.loginOrRegisterWithGoogle("bad-token"))
                .thenThrow(new IllegalArgumentException("Token Google invalide ou expiré."));
        when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/auth/google");

        ResponseEntity<Object> response = authController.loginWithGoogle(
                new GoogleLoginRequest("bad-token"), httpServletRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("Token Google invalide ou expiré.", body.message());
    }

}
