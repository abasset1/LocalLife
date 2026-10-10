package com.locallife.backend.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class UsernamePolicyTest {

    @Mock
    private UserRepository userRepository;

    private UsernamePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new UsernamePolicy(userRepository);
    }

    private static User user(Long id, String username) {
        return new User(id, username, username + "@example.com", "hash", Role.USER, LocalDateTime.now(), true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "Alice", "jean.dupont", "jean_dupont", "jean-dupont", "42fan",
        "a23456789012345678901234567890"})
    void validate_ShouldAccept_WellFormedFreeNames(String username) {
        assertEquals(username, policy.validate(username, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "a234567890123456789012345678901", "jean dupont", "élodie", "-alice", ".alice",
        "_alice", "alice@home", "alice!"})
    void validate_ShouldReject_MalformedNames(String username) {
        assertThrows(IllegalArgumentException.class, () -> policy.validate(username, null));
    }

    @Test
    void validate_ShouldRejectBlank_WithRequiredMessage() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> policy.validate("   ", null));
        assertEquals("Le nom d'utilisateur est requis.", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "ADMIN", "Support", "locallife", "moderateur"})
    void validate_ShouldReject_ReservedNamesWhateverTheCase(String username) {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> policy.validate(username, null));
        assertEquals("Ce nom d'utilisateur est réservé.", error.getMessage());
    }

    @Test
    void validate_ShouldReject_NameTakenWithAnotherCase() {
        when(userRepository.findByUsernameIgnoreCase("ALICE")).thenReturn(Optional.of(user(1L, "alice")));

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> policy.validate("ALICE", 2L));
        assertEquals("Ce nom d'utilisateur est déjà pris.", error.getMessage());
    }

    @Test
    void validateForBootstrap_ShouldAllowReservedName() {
        assertEquals("admin", policy.validateForBootstrap("admin"));
    }

    @Test
    void unavailabilityReason_ShouldBeEmpty_WhenNameFree() {
        assertTrue(policy.unavailabilityReason("alice").isEmpty());
        assertEquals(Optional.of("Ce nom d'utilisateur est réservé."), policy.unavailabilityReason("admin"));
    }

    @Test
    void generateAvailable_ShouldSanitizeDisplayName() {
        assertEquals("Elodie-Durand", policy.generateAvailable("Élodie Durand"));
        assertEquals("jean.dupont", policy.generateAvailable("jean.dupont"));
        assertEquals("membre", policy.generateAvailable("李"));
        assertEquals("membre", policy.generateAvailable(null));
        assertEquals(30, policy.generateAvailable("a".repeat(50)).length());
    }

    @Test
    void generateAvailable_ShouldSuffix_WhenTakenOrReserved() {
        Set<String> taken = Set.of("alice", "alice-2");
        when(userRepository.findByUsernameIgnoreCase(any())).thenAnswer(invocation ->
                taken.contains(((String) invocation.getArgument(0)).toLowerCase())
                        ? Optional.of(user(1L, invocation.getArgument(0)))
                        : Optional.empty());

        assertEquals("Alice-3", policy.generateAvailable("Alice"));
        assertEquals("admin-2", policy.generateAvailable("admin"));
        String longName = policy.generateAvailable("alice" + "x".repeat(40));
        assertEquals(30, longName.length());
    }

    @Test
    void saveGuarded_ShouldTranslateUniqueIndexViolation() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> policy.saveGuarded(() -> {
                    throw new DuplicateKeyException(
                            "duplicate key value violates unique constraint \"users_username_lower_key\"");
                }));
        assertEquals("Ce nom d'utilisateur est déjà pris.", error.getMessage());
    }

    @Test
    void saveGuarded_ShouldRethrowOtherIntegrityViolations() {
        assertThrows(DataIntegrityViolationException.class,
                () -> policy.saveGuarded(() -> {
                    throw new DataIntegrityViolationException("autre contrainte");
                }));
    }
}
