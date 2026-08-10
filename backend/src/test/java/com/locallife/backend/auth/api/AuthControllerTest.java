package com.locallife.backend.auth.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;

import com.locallife.backend.auth.application.AuthService;
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
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AuthController authController;

    @Test
    void register_ShouldReturnCreated_WithUserWithoutPasswordHash() {
        User created = new User(1L, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now());
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

}
