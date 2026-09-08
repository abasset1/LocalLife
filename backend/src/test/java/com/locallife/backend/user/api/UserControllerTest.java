package com.locallife.backend.user.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.auth.api.UserResponse;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.user.api.UserController.CreateUserRequest;
import com.locallife.backend.user.api.UserController.UpdateProfileRequest;
import com.locallife.backend.user.application.UserService;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Correctif LL-3010 (fuite signalée) : le contrôleur renvoie désormais
 * {@link UserResponse} et non plus l'entité {@code User}. Le type de
 * retour à lui seul garantit qu'aucun {@code passwordHash} ne peut
 * transiter dans la réponse — {@code UserResponse} ne déclare pas ce
 * composant, donc toute régression serait une erreur de compilation.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private UserController userController;

    @Test
    void createUser_ShouldReturnCreated_WithUserResponse() {
        // Given
        User created = new User(1L, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userService.createUser("alice", "alice@example.com")).thenReturn(created);

        // When
        ResponseEntity<UserResponse> response = userController.createUser(
                new CreateUserRequest("alice", "alice@example.com"));

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("alice", response.getBody().username());
        assertEquals("alice@example.com", response.getBody().email());
    }

    @Test
    void getUserById_ShouldReturnUserResponse_WhenFound() {
        // Given
        User user = new User(1L, "bob", "bob@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userService.getUserById(1L)).thenReturn(Optional.of(user));

        // When
        ResponseEntity<UserResponse> response = userController.getUserById(1L);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("bob", response.getBody().username());
    }

    @Test
    void getUserById_ShouldReturnNotFound_WhenNotFound() {
        // Given
        when(userService.getUserById(999L)).thenReturn(Optional.empty());

        // When
        ResponseEntity<UserResponse> response = userController.getUserById(999L);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getCurrentUser_ShouldReturnOwnProfile_FromJwtUserId() {
        // Given : userId provient exclusivement du JWT (JwtAuthentication), jamais
        // d'un paramètre de requête — voir la javadoc de UserController#getCurrentUser.
        User user = new User(7L, "carol", "carol@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userService.getUserById(7L)).thenReturn(Optional.of(user));
        JwtAuthentication authentication = new JwtAuthentication(7L, "carol@example.com", Role.USER);

        // When
        ResponseEntity<UserResponse> response = userController.getCurrentUser(authentication);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("carol", response.getBody().username());
    }

    @Test
    void updateCurrentUser_ShouldReturnUpdatedProfile_WhenValid() {
        // Given
        User updated = new User(7L, "carol2", "carol2@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userService.updateProfile(7L, "carol2", "carol2@example.com")).thenReturn(updated);
        JwtAuthentication authentication = new JwtAuthentication(7L, "carol@example.com", Role.USER);

        // When
        ResponseEntity<Object> response = userController.updateCurrentUser(
                authentication, new UpdateProfileRequest("carol2", "carol2@example.com"), httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("carol2", ((UserResponse) response.getBody()).username());
    }

    @Test
    void updateCurrentUser_ShouldReturnBadRequest_WhenServiceRejectsInput() {
        // Given
        when(userService.updateProfile(7L, "carol", "email-invalide"))
                .thenThrow(new IllegalArgumentException("L'adresse email n'est pas valide."));
        JwtAuthentication authentication = new JwtAuthentication(7L, "carol@example.com", Role.USER);

        // When
        ResponseEntity<Object> response = userController.updateCurrentUser(
                authentication, new UpdateProfileRequest("carol", "email-invalide"), httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

}
