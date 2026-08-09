package com.locallife.backend.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void createUser_ShouldSaveAndReturnUser() {
        // Given
        User saved = new User(1L, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userRepository.save(any(User.class))).thenReturn(saved);

        // When
        User result = userService.createUser("alice", "alice@example.com");

        // Then
        assertEquals(1L, result.id());
        assertEquals("alice", result.username());
        assertEquals("alice@example.com", result.email());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void getUserById_ShouldReturnUser_WhenFound() {
        // Given
        User user = new User(1L, "bob", "bob@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // When
        Optional<User> result = userService.getUserById(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals("bob", result.get().username());
    }

    @Test
    void getUserById_ShouldReturnEmpty_WhenNotFound() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<User> result = userService.getUserById(999L);

        // Then
        assertFalse(result.isPresent());
    }

}
