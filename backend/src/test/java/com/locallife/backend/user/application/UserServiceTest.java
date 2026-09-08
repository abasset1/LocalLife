package com.locallife.backend.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void updateProfile_ShouldSaveAndReturnUpdatedUser_WhenValid() {
        // Given
        User existing = new User(1L, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("alice2@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateProfile(1L, "alice2", "alice2@example.com");

        // Then
        assertEquals("alice2", result.username());
        assertEquals("alice2@example.com", result.email());
        // role/passwordHash/createdAt/id ne doivent jamais changer par ce chemin
        // (voir la javadoc de updateProfile).
        assertEquals(Role.USER, result.role());
        assertEquals("hash", result.passwordHash());
        assertEquals(1L, result.id());
    }

    @Test
    void updateProfile_ShouldAllowKeepingOwnEmail_Unchanged() {
        // Given : l'email n'a pas changé — ne doit pas être rejeté comme
        // "déjà utilisé" simplement parce qu'il appartient à l'utilisateur
        // lui-même (voir UserService#validateProfileUpdate).
        User existing = new User(1L, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateProfile(1L, "alice-nouveau-pseudo", "alice@example.com");

        // Then
        assertEquals("alice-nouveau-pseudo", result.username());
    }

    @Test
    void updateProfile_ShouldThrow_WhenEmailAlreadyUsedByAnotherUser() {
        // Given
        User existing = new User(1L, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now());
        User otherUser = new User(2L, "bob", "bob@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(otherUser));

        // When / Then
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateProfile(1L, "alice", "bob@example.com"));
    }

    @Test
    void updateProfile_ShouldThrow_WhenUsernameBlank() {
        // Given
        User existing = new User(1L, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        // When / Then
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateProfile(1L, "  ", "alice@example.com"));
    }

    @Test
    void updateProfile_ShouldThrow_WhenEmailInvalid() {
        // Given
        User existing = new User(1L, "alice", "alice@example.com", "hash", Role.USER, LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        // When / Then
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateProfile(1L, "alice", "pas-un-email"));
    }

    @Test
    void updateProfile_ShouldThrowNoSuchElement_WhenUserNotFound() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(
                java.util.NoSuchElementException.class,
                () -> userService.updateProfile(999L, "alice", "alice@example.com"));
    }

}
