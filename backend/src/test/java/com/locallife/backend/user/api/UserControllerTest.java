package com.locallife.backend.user.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.user.api.UserController.CreateUserRequest;
import com.locallife.backend.user.application.UserService;
import com.locallife.backend.user.domain.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @Test
    void createUser_ShouldReturnCreated_WithUser() {
        // Given
        User created = new User(1L, "alice", "alice@example.com", LocalDateTime.now());
        when(userService.createUser("alice", "alice@example.com")).thenReturn(created);

        // When
        ResponseEntity<User> response = userController.createUser(new CreateUserRequest("alice", "alice@example.com"));

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("alice", response.getBody().username());
        assertEquals("alice@example.com", response.getBody().email());
    }

    @Test
    void getUserById_ShouldReturnUser_WhenFound() {
        // Given
        User user = new User(1L, "bob", "bob@example.com", LocalDateTime.now());
        when(userService.getUserById(1L)).thenReturn(Optional.of(user));

        // When
        ResponseEntity<User> response = userController.getUserById(1L);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("bob", response.getBody().username());
    }

    @Test
    void getUserById_ShouldReturnNotFound_WhenNotFound() {
        // Given
        when(userService.getUserById(999L)).thenReturn(Optional.empty());

        // When
        ResponseEntity<User> response = userController.getUserById(999L);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

}
